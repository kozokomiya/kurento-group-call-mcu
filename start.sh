# /bin/bash

# HOST name or IP Address of kurento-media-server HOST
HOST_ADR=xx.xx.xx.xx

mvn -U clean spring-boot:run -Dspring-boot.run.jvmArguments="-Dkms.url=ws://${HOST_ADR}:8888/kurento"
