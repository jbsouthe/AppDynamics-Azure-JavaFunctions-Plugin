# AppDynamics-Azure-JavaFunctions-Plugin

This plugin will create Servlet Business Transactions for Java Functions in Azure running the Appdynamics Java Agent.
- If it can't get a valid URL from the request, it will fallback to a POJO Business Transaction.
- If it can find a correlation header in the incoming request, it will continue the Business Transaction it was called from.
- Collect Snapshot and Analytics data with the Transaction for: FunctionId, InvocationId, and FunctionName

Please create an issue and let us know if anything more should or could be added.

## Required
- Agent version 21.3+
- Java 8


## Deployment steps
- Copy Agent Plugin jar file into <agent-install-dir>/ver.x.x.x.x/sdk-plugins


