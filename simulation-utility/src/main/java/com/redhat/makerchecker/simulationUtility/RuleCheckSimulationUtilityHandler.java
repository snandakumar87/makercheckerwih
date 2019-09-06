package com.redhat.makerchecker.simulationUtility;

import java.util.*;

import com.CustomerHistory;
import com.Event;

import com.RulesFired;
import org.drools.core.process.instance.WorkItemHandler;
import org.kie.api.KieServices;

import org.kie.api.command.BatchExecutionCommand;
import org.kie.api.command.Command;
import org.kie.api.command.KieCommands;

import org.kie.api.runtime.ExecutionResults;
import org.kie.api.runtime.process.WorkItem;
import org.kie.api.runtime.process.WorkItemManager;
import org.kie.server.api.marshalling.Marshaller;
import org.kie.server.api.marshalling.MarshallerFactory;
import org.kie.server.api.marshalling.MarshallingFormat;
import org.kie.server.api.model.*;
import org.kie.server.client.KieServicesClient;
import org.kie.server.client.KieServicesConfiguration;
import org.kie.server.client.KieServicesFactory;
import org.kie.server.client.RuleServicesClient;


public class RuleCheckSimulationUtilityHandler implements WorkItemHandler {
	private static final String URL = "http://localhost:8080/kie-server/services/rest/server";
	private static final String USER = "pamAdmin";
	private static final String PASSWORD = "redhatpam1!";
	private static final MarshallingFormat FORMAT = MarshallingFormat.JSON;

	private static KieServicesConfiguration conf;
	private static KieServicesClient kieServicesClient;
	private static final String STATELESS_KIE_SESSION_ID = "default-stateless-ksession";

	private static final String[] customerAttribute = { "CUSTOMER_GOOD_STANDING","OFFER_UPGRADE","HIGH_BALANCE_DEBT","DISSATISFACTION_QUOTIENT","DELINQUENCY_INDEX"};

	private static final String[] eventCategory = {"CC_BALANCE_PAYMENT","CC_TRANSACTION","DISPUTES","ONLINE_ACCOUNT"};
	private static final String[] eventValue = {"LATE_PAYMENT","AIRLINE_PURCHASE","MIN_DUE","CASE_CREATED","PAYMENT_FAILURE"};

	public static void initialize() {
		conf = KieServicesFactory.newRestConfiguration(URL, USER, PASSWORD);
		conf.setMarshallingFormat(FORMAT);
		kieServicesClient = KieServicesFactory.newKieServicesClient(conf);
	}



	public void abortWorkItem(WorkItem wi, WorkItemManager wim) {


	}

	public void executeWorkItem(WorkItem wi, WorkItemManager wim) {

		String containerName = (String) wi.getParameter("containerName");
		String artifactName = (String)wi.getParameter("artifactName");

		initialize();
		System.out.println("Container name here"+containerName);
		//Run simulation for previous deployment
		List<RulesFired> rulesFiredList = invokeCartesianProductCombination(containerName);
		//Run simulation for new deployment
		List<RulesFired> baseRulesFired = invokeCartesianProductCombination(artifactName+"_Baseline");


		String summaryOfChanges;
		List<RulesFired> delta = null;
		//Create summary of changes to show if rules were added/removed/changed
		if(rulesFiredList.size() > baseRulesFired.size()) {
			delta= new ArrayList<>(rulesFiredList);
			delta.removeAll(baseRulesFired);
			summaryOfChanges = "Rules Added";
		} else if(rulesFiredList.size() < baseRulesFired.size()) {
			delta = new ArrayList<>(baseRulesFired);
			delta.removeAll(rulesFiredList);
			summaryOfChanges = "Rules Removed";
		} else {
			summaryOfChanges = "Rules Changed";
		}



		Map<String,Object> resultMap = new HashMap<>();
		resultMap.put("simualationResults",rulesFiredList);
		resultMap.put("baseRuleSimulationResults",baseRulesFired);
        resultMap.put("delta",delta);
		resultMap.put("summaryOfChanges",summaryOfChanges);
		wim.completeWorkItem(wi.getId(), resultMap);
	}

	public List<RulesFired> invokeCartesianProductCombination(String containerName) {

		RuleServicesClient rulesClient = kieServicesClient.getServicesClient(RuleServicesClient.class);

		List<Command> commands = new ArrayList<Command>();

		KieCommands commandFactory = KieServices.Factory.get().getCommands();

		int[] lengths = new int[] { customerAttribute.length, eventCategory.length, eventValue.length};
		Set<RulesFired> rulesFiredList = new TreeSet<>();
		Event event = null;
		CustomerHistory customerHistory = null;
		List<RulesFired> rulesExecuted = new ArrayList<>();
		//For every cartesian combination of inputs, create input fact models so that it can be inserted for rule processing
		for (int[] indices : new CartesianProduct(lengths)) {
			event = new Event();
			customerHistory = new CustomerHistory();
			event.setEventCategory(eventCategory[indices[1]]);
			event.setEventValue(eventValue[indices[2]]);
			customerHistory.setCustomerAttribute(customerAttribute[indices[0]]);
			commands.add(commandFactory.newInsert(customerHistory,"txn1"));
			commands.add(commandFactory.newInsert(event,"txn1"));

			commands.add(commandFactory.newFireAllRules());
			commands.add(commandFactory.newGetObjects("txn1"));


			BatchExecutionCommand batchExecutionCommand = commandFactory.newBatchExecution(commands,STATELESS_KIE_SESSION_ID);

			ServiceResponse<ExecutionResults> response = rulesClient.executeCommandsWithResults(containerName, batchExecutionCommand);


			if (response.getType() == KieServiceResponse.ResponseType.SUCCESS) {
				ExecutionResults results = response.getResult();

				Marshaller marshaller = MarshallerFactory.getMarshaller(conf.getExtraClasses(), conf.getMarshallingFormat(),
						Thread.currentThread().getContextClassLoader());
				String json = marshaller.marshall(results);
				List<Object> rulesFired = (List<Object>) results.getValue("txn1");
				RulesFired parsedObject = null;

				for(Object obj: rulesFired) {

					LinkedHashMap<String,Object> returnMap = (LinkedHashMap<String, Object>) obj;

					LinkedHashMap<String,Object> valueMap = (LinkedHashMap<String, Object>) returnMap.get("com.RulesFired");
					if(null != valueMap) {

					    parsedObject = new RulesFired();
					    parsedObject.setEventCategory((String)valueMap.get("eventCategory"));
						parsedObject.setEventValue((String)valueMap.get("eventValue"));
						parsedObject.setCustomerHistory((String)valueMap.get("customerHistory"));
						parsedObject.setEventEffectiveness((String)valueMap.get("eventEffectiveness"));
						parsedObject.setEventResponsePayload((String)valueMap.get("eventResponsePayload"));
						parsedObject.setRuleString((String)valueMap.get("ruleString"));

						rulesFiredList.add((RulesFired) parsedObject);
						System.out.println(parsedObject);
					}
				}
			}
		}
		//Read all the rules executed (We have a custom Object list which we use to collect Rule Changes as they happen, so
		//that they can then use to present the simulation.
		rulesExecuted.addAll(rulesFiredList);

		System.out.println(rulesExecuted);


		return rulesExecuted;
	}



}
