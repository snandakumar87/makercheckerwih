package com.redhat.makerchecker.promoteReleaseId;

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

public class PromoteReleaseIdWorkItemHandler implements WorkItemHandler {
	private static final String URL = "http://localhost:8080/kie-server/services/rest/server";
	private static final String USER = "rhpamAdmin";
	private static final String PASSWORD = "password";
	private static final MarshallingFormat FORMAT = MarshallingFormat.JSON;

	private static KieServicesConfiguration conf;
	private static KieServicesClient kieServicesClient;
	private static final String FILE_PATH="/Users/sadhananandakumar/Documents/Demos/service-repo/ShellCall/src/release_jar.sh";


	public static void initialize() {
		conf = KieServicesFactory.newRestConfiguration(URL, USER, PASSWORD);
		conf.setMarshallingFormat(FORMAT);
		kieServicesClient = KieServicesFactory.newKieServicesClient(conf);
	}

	public static void getContainers(String artifactId, String containerId) {


		System.out.println("artifactname"+artifactId);
		System.out.println("containerName"+containerId);

		String containerForReview = null;
		KieContainerResourceFilter kieContainerResourceFilter = new KieContainerResourceFilter(new ReleaseIdFilter(null,artifactId,null));

		// Retrieve list of KIE containers
		List<KieContainerResource> kieContainers = kieServicesClient.listContainers(kieContainerResourceFilter).getResult().getContainers();

		//Split the release version string to upgrade. The idea here is to upgrade from for example
		//1.0.0-SNAPSHOT to 1.0.0
		String[] containerVersion = containerId.split("_");
		String[] releaseVersion = containerVersion[2].split("-");
		System.out.println("Release Version"+releaseVersion);
		String release = releaseVersion[0];

		System.out.println("Release here"+release);
		ReleaseId releaseId=null;

		for(KieContainerResource container:kieContainers) {

			if(container.getContainerId().equals(containerId)){

				releaseId = container.getReleaseId();

				ServiceResponse<ReleaseId> resp = kieServicesClient.updateReleaseId(artifactId+"_Baseline", container.getReleaseId());

			}
			//Dispose container after upgrading release version
			kieServicesClient.disposeContainer(containerId);
		}


		try {
			//Build the latest release jar
			invokeShellScript(release);
		} catch (Exception e) {
			e.printStackTrace();
		}




	}



	public void abortWorkItem(WorkItem wi, WorkItemManager wim) {


	}

	public void executeWorkItem(WorkItem wi, WorkItemManager wim) {

		String artifactName = (String) wi.getParameter("artifactName");
		String containerName = (String) wi.getParameter("containerForReview");

		initialize();
		getContainers(artifactName, containerName);



		wim.completeWorkItem(wi.getId(), null);
	}

	public static void  invokeShellScript(String version) throws Exception{

		String[] cmd = { "sh", FILE_PATH,version};
		Process p = Runtime.getRuntime().exec(cmd);
	}


}
