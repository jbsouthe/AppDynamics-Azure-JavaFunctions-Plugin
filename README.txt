==================================
## Required
- Agent version 4.3+
- Java 8


## Deployment steps
- Copy Agent Plugin jar file into <agent-install-dir>/ver.x.x.x.x/sdk-plugins
- Add this system property java option in startup

    -Dallow.unsigned.sdk.extension.jars=true
    
- Restart the JVM
    
    
