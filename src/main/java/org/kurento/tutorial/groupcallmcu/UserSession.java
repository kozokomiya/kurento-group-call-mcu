/*
 * (C) Copyright 2014 Kurento (http://kurento.org/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.kurento.tutorial.groupcallmcu;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.kurento.client.Continuation;
import org.kurento.client.EventListener;
import org.kurento.client.HubPort;
import org.kurento.client.IceCandidate;
import org.kurento.client.IceCandidateFoundEvent;
import org.kurento.client.MediaType;
import org.kurento.client.WebRtcEndpoint;
import org.kurento.jsonrpc.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.google.gson.JsonObject;

/**
 *
 * @author Ivan Gracia (izanmail@gmail.com)
 * @since 4.3.1
 */
public class UserSession implements Closeable {

  private static final Logger log = LoggerFactory.getLogger(UserSession.class);

  private final String name;
  private final WebSocketSession session;

  private final Room room;
  private final WebRtcEndpoint outgoingMedia;
  private final WebRtcEndpoint mixerMedia;
  private final HubPort mixerHub;
  private final ConcurrentMap<String, WebRtcEndpoint> incomingMedia = new ConcurrentHashMap<>();

  public UserSession(final String name, Room room, final WebSocketSession session) {

    this.room = room;
    this.name = name;
    this.session = session;
    this.outgoingMedia = new WebRtcEndpoint.Builder(this.room.getPipeline()).build();
    this.mixerMedia = new WebRtcEndpoint.Builder(this.room.getPipeline()).build();
    this.mixerHub = new HubPort.Builder(this.room.getMixer()).build();
    this.room.getMixerOut().connect(mixerMedia, MediaType.VIDEO);
    this.outgoingMedia.connect(mixerHub);
    this.outgoingMedia.addIceCandidateFoundListener(new EventListener<IceCandidateFoundEvent>() {

      @Override
      public void onEvent(IceCandidateFoundEvent event) {
        JsonObject response = new JsonObject();
        response.addProperty("id", "iceCandidate");
        response.addProperty("name", name);
        response.add("candidate", JsonUtils.toJsonObject(event.getCandidate()));
        try {
          synchronized (session) {
            session.sendMessage(new TextMessage(response.toString()));
          }
        } catch (IOException e) {
          log.debug(e.getMessage());
        }
      }
    });
    this.mixerMedia.addIceCandidateFoundListener(new EventListener<IceCandidateFoundEvent>() {

      @Override
      public void onEvent(IceCandidateFoundEvent event) {
        JsonObject response = new JsonObject();
        response.addProperty("id", "iceCandidate");
        response.addProperty("name", UserSession.this.getRoomName());
        response.add("candidate", JsonUtils.toJsonObject(event.getCandidate()));
        try {
          synchronized (session) {
            session.sendMessage(new TextMessage(response.toString()));
          }
        } catch (IOException e) {
          log.debug(e.getMessage());
        }
      }
    });

  }

  public WebRtcEndpoint getOutgoingWebRtcPeer() {
    return outgoingMedia;
  }

  public String getName() {
    return name;
  }

  public String getRoomName() {
    return this.room.getName();
  }

  public WebSocketSession getSession() {
    return session;
  }

  public void receiveVideoFrom(String senderName, String sdpOffer) throws IOException {
    log.info("USER {}: connecting with {} in room {}", this.name, senderName, this.getRoomName());

    log.trace("USER {}: SdpOffer for {} is {}", this.name, senderName, sdpOffer);

    final String ipSdpAnswer = this.getEndpointForUser(senderName).processOffer(sdpOffer);
    final JsonObject scParams = new JsonObject();
    scParams.addProperty("id", "receiveVideoAnswer");
    scParams.addProperty("name", senderName);
    scParams.addProperty("sdpAnswer", ipSdpAnswer);

    log.trace("USER {}: SdpAnswer for {} is {}", this.name, senderName, ipSdpAnswer);
    this.sendMessage(scParams);
    log.debug("gather candidates");
    this.getEndpointForUser(senderName).gatherCandidates();
  }

  private WebRtcEndpoint getEndpointForUser(final String senderName) {
    if (senderName.equals(name)) {
      log.debug("PARTICIPANT {}: configuring loopback", this.name);
      return outgoingMedia;
    } else if (senderName.equals(this.getRoomName())) {
      log.debug("PARTICIPANT {}: receiving video from mixer", this.name);
      return mixerMedia;
    }

    final UserSession sender = this.room.getParticipant(senderName);
    log.debug("PARTICIPANT {}: receiving video from {}", this.name, sender.getName());
    WebRtcEndpoint incoming = incomingMedia.get(sender.getName());
    if (incoming == null) {
      log.debug("PARTICIPANT {}: creating new endpoint for {}", this.name, sender.getName());
      incoming = new WebRtcEndpoint.Builder(this.room.getPipeline()).build();

      incoming.addIceCandidateFoundListener(new EventListener<IceCandidateFoundEvent>() {

        @Override
        public void onEvent(IceCandidateFoundEvent event) {
          JsonObject response = new JsonObject();
          response.addProperty("id", "iceCandidate");
          response.addProperty("name", sender.getName());
          response.add("candidate", JsonUtils.toJsonObject(event.getCandidate()));
          try {
            synchronized (session) {
              session.sendMessage(new TextMessage(response.toString()));
            }
          } catch (IOException e) {
            log.debug(e.getMessage());
          }
        }
      });

      incomingMedia.put(sender.getName(), incoming);
    }

    log.debug("PARTICIPANT {}: obtained endpoint for {}", this.name, sender.getName());
    sender.getOutgoingWebRtcPeer().connect(incoming);

    return incoming;
  }

  public void cancelVideoFrom(final UserSession sender) {
    this.cancelVideoFrom(sender.getName());
  }

  public void cancelVideoFrom(final String senderName) {
    log.debug("PARTICIPANT {}: canceling video reception from {}", this.name, senderName);
    final WebRtcEndpoint incoming = incomingMedia.remove(senderName);

    log.debug("PARTICIPANT {}: removing endpoint for {}", this.name, senderName);
    incoming.release(new Continuation<Void>() {
      @Override
      public void onSuccess(Void result) throws Exception {
        log.trace("PARTICIPANT {}: Released successfully incoming EP for {}",
            UserSession.this.name, senderName);
      }

      @Override
      public void onError(Throwable cause) throws Exception {
        log.warn("PARTICIPANT {}: Could not release incoming EP for {}", UserSession.this.name,
            senderName);
      }
    });
  }

  @Override
  public void close() throws IOException {    
    log.debug("PARTICIPANT {}: Releasing resources", this.name);
    mixerHub.release();
    for (final String remoteParticipantName : incomingMedia.keySet()) {

      log.trace("PARTICIPANT {}: Released incoming EP for {}", this.name, remoteParticipantName);

      final WebRtcEndpoint ep = this.incomingMedia.get(remoteParticipantName);

      ep.release(new Continuation<Void>() {

        @Override
        public void onSuccess(Void result) throws Exception {
          log.trace("PARTICIPANT {}: Released successfully incoming EP for {}",
              UserSession.this.name, remoteParticipantName);
        }

        @Override
        public void onError(Throwable cause) throws Exception {
          log.warn("PARTICIPANT {}: Could not release incoming EP for {}", UserSession.this.name,
              remoteParticipantName);
        }
      });
    }

    outgoingMedia.release(new Continuation<Void>() {

      @Override
      public void onSuccess(Void result) throws Exception {
        log.trace("PARTICIPANT {}: Released outgoing EP", UserSession.this.name);
      }

      @Override
      public void onError(Throwable cause) throws Exception {
        log.warn("USER {}: Could not release outgoing EP", UserSession.this.name);
      }
    });

    mixerMedia.release(new Continuation<Void>() {
      @Override
      public void onSuccess(Void result) throws Exception {
        log.trace("PARTICIPANT {}: Released mixer EP", UserSession.this.name);
      }

      @Override
      public void onError(Throwable cause) throws Exception {
        log.warn("USER {}: Could not release mixer EP", UserSession.this.name);
      }
    });
  }

  public void sendMessage(JsonObject message) throws IOException {
    log.debug("USER {}: Sending message {}", name, message);
    synchronized (session) {
      session.sendMessage(new TextMessage(message.toString()));
    }
  }

  public void addCandidate(IceCandidate candidate, String name) {
    if (this.name.compareTo(name) == 0) {
      outgoingMedia.addIceCandidate(candidate);
    } else if (this.name.compareTo(this.getRoomName()) == 0) {
      mixerMedia.addIceCandidate(candidate);
    } else {
      WebRtcEndpoint webRtc = incomingMedia.get(name);
      if (webRtc != null) {
        webRtc.addIceCandidate(candidate);
      }
    }
  }

  /*
   * (non-Javadoc)
   *
   * @see java.lang.Object#equals(java.lang.Object)
   */
  @Override
  public boolean equals(Object obj) {

    if (this == obj) {
      return true;
    }
    if (obj == null || !(obj instanceof UserSession)) {
      return false;
    }
    UserSession other = (UserSession) obj;
    boolean eq = name.equals(other.name);
    eq &= this.getRoomName().equals(other.getRoomName());
    return eq;
  }

  /*
   * (non-Javadoc)
   *
   * @see java.lang.Object#hashCode()
   */
  @Override
  public int hashCode() {
    int result = 1;
    result = 31 * result + name.hashCode();
    result = 31 * result + this.getRoomName().hashCode();
    return result;
  }
}
