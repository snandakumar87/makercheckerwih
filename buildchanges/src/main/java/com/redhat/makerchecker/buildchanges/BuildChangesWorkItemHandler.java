package com.redhat.makerchecker.buildchanges;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.drools.core.process.instance.WorkItemHandler;
import org.kie.api.runtime.process.WorkItem;
import org.kie.api.runtime.process.WorkItemManager;
import org.kie.server.api.marshalling.MarshallingFormat;
import org.kie.server.api.model.*;
import org.kie.server.client.KieServicesClient;
import org.kie.server.client.KieServicesConfiguration;
import org.kie.server.client.KieServicesFactory;

public class BuildChangesWorkItemHandler implements WorkItemHandler {
	private static final String URL = "http://localhost:8080/kie-server/services/rest/server";
	private static final String USER = "rhpamAdmin";
	private static final String PASSWORD = "password";
	private static final String BUILD_JAR_PATH = "/Users/sadhananandakumar/Desktop/hello-world-work-item-handler-master/buildchanges/src/main/resources/build_jar.sh";
	private static final MarshallingFormat FORMAT = MarshallingFormat.JSON;

	private static KieServicesConfiguration conf;
	private static KieServicesClient kieServicesClient;


	public static String identifyVersionToBuild(String artifactName) {
		conf = KieServicesFactory.newRestConfiguration(URL, USER, PASSWORD);
		conf.setMarshallingFormat(FORMAT);
		kieServicesClient = KieServicesFactory.newKieServicesClient(conf);
		String containerForReview = null;
		KieContainerResourceFilter kieContainerResourceFilter = new KieContainerResourceFilter(new ReleaseIdFilter(null,artifactName,null));
		String revisedVersion = "";
		//List containers for artifact
		List<KieContainerResource> kieContainers = kieServicesClient.listContainers(kieContainerResourceFilter).getResult().getContainers();
		for(KieContainerResource kieContainerResource:kieContainers) {
			//Identify the artifact created for Baseline container
			//Baseline is the golden copy container which holds the latest checker approved version of the artifact
			if(kieContainerResource.getContainerId().equals(artifactName+"_Baseline")) {
				String version = kieContainerResource.getReleaseId().getVersion();
				if(!version.equals("Baseline")) {
					//Identify the last approved version, so that we can increment by one and create a snapshot version for review
					String versionString[] = version.split("\\.");
					int versionInt = Integer.parseInt(versionString[1]) + 1;
					revisedVersion += versionString[0] + "." + versionInt + "." + versionString[2];
				}else {
					//For the first deployment after the golden copy.
					revisedVersion = "1.0.0-SNAPSHOT";
				}
			}
		}

		try {
			//Invoke the shell script, so that the jar gets built with the specified version.
			invokeShellScript(revisedVersion);
		} catch (Exception e) {
			e.printStackTrace();
		}

		KieContainerResource newContainer = new KieContainerResource();
		newContainer.setReleaseId(new ReleaseId("com.redhat",artifactName,revisedVersion));
		//wait for the build to complete
		try {
			Thread.sleep(20000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		//Create a new container with the same artifact/version notation
		kieServicesClient.createContainer(artifactName+"_"+revisedVersion,newContainer);
		return artifactName+"_"+revisedVersion;
	}


	public static void invokeShellScript(String version) throws Exception{
		File file = new File(BUILD_JAR_PATH);
		String[] cmd = { "sh", BUILD_JAR_PATH,version};
		Process p = Runtime.getRuntime().exec(cmd);
	}



	public void abortWorkItem(WorkItem wi, WorkItemManager wim) {


	}

	public void executeWorkItem(WorkItem wi, WorkItemManager wim) {

		String artifactName = (String) wi.getParameter("artifactName");
		String container = identifyVersionToBuild(artifactName);
		Map<String,Object> resultMap = new HashMap();
		resultMap.put("containerName",container);

		wim.completeWorkItem(wi.getId(), resultMap);

	}




}
