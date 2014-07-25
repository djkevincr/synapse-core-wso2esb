/*
* Licensed to the Apache Software Foundation (ASF) under one
* or more contributor license agreements. See the NOTICE file
* distributed with this work for additional information
* regarding copyright ownership. The ASF licenses this file
* to you under the Apache License, Version 2.0 (the
* "License"); you may not use this file except in compliance
* with the License. You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied. See the License for the
* specific language governing permissions and limitations
* under the License.
*/

package org.apache.synapse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.protocol.HTTP;
import org.apache.synapse.config.SynapseConfiguration;
import org.apache.synapse.config.xml.XMLConfigConstants;
import org.apache.synapse.core.SynapseEnvironment;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.core.axis2.ProxyService;
import org.apache.synapse.mediators.AbstractListMediator;
import org.apache.synapse.mediators.AbstractMediator;
import org.apache.synapse.rest.RESTConstants;
import org.apache.synapse.rest.Resource;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;


public class SynapseDebugManager {

    private MessageContext synCtx;
    private SynapseDebugInterface debugInterface;
    private static SynapseDebugManager debugManagerInstance = null;
    private SynapseConfiguration synCfg;
    private SynapseEnvironment synEnv;
    private MediationFlowState medFlowState;
    private String msgReceiver;
    private String msgCallbackReceiver;
    private boolean initialised=false;
    private static final Log log = LogFactory.getLog(SynapseDebugManager.class);

   protected SynapseDebugManager() {

   }

   public void setMessageContext(MessageContext synCtx){
        this.synCtx = synCtx;
    }


   public static SynapseDebugManager getInstance() {
        if(debugManagerInstance == null) {
            debugManagerInstance = new SynapseDebugManager();
        }
        return debugManagerInstance;
    }


    public void init(SynapseConfiguration synCfg, SynapseDebugInterface debugInterface,SynapseEnvironment synEnv ){
        if(synEnv.isDebugEnabled()){
            this.synCfg = synCfg;
            this.debugInterface = debugInterface;
            this.synEnv = synEnv;
            if(!initialised) {
                initialised=true;
                log.info("Initialized with Synapse Configuration");
            }else{
                log.info("Updated Synapse Configuration");
            }
        }

    }

    public void closeCommunicationChannel(){
        if(synEnv.isDebugEnabled()){
            debugInterface.closeConnection();
        }

    }

    public void advertiseMediationFlowStartPoint(String messageReceiver){
        setMessageContext(synCtx);
        //medFlowState=MediationFlowState.STARTED;
        this.msgReceiver=messageReceiver;
        advertiseDebugEvent(SynapseDebugEventConstants.DEBUG_EVENT_STARTED+" "+messageReceiver);
        //log.info("DEBUG EVENT - "+SynapseDebugEventConstants.DEBUG_EVENT_STARTED+" "+messageReceiver);
        medFlowState=MediationFlowState.SUSPENDED;
        String debug_line=null;
        try {
            while (medFlowState.equals(MediationFlowState.SUSPENDED)) {
                if (debugInterface.getPortListenReader().ready()) {
                    debug_line = debugInterface.getPortListenReader().readLine();
                }
                if (debug_line != null) {
                    processDebugCommand(debug_line);
                    debug_line = null;
                }
            }
            advertiseDebugEvent(SynapseDebugEventConstants.DEBUG_EVENT_RESUMED_CLIENT);
            //log.info("DEBUG EVENT - "+SynapseDebugEventConstants.DEBUG_EVENT_RESUMED_CLIENT);
        }catch (IOException ex){
            log.error("Error listening the command port at mediation flow start up");
        }
    }

    public void advertiseMediationFlowCallbackPoint(String messageCallbackReceiver){
       // setMessageContext(synCtx);
        this.msgCallbackReceiver=messageCallbackReceiver;
        //medFlowState=MediationFlowState.CONTINUED;
        advertiseDebugEvent(SynapseDebugEventConstants.DEBUG_EVENT_CONTINUED_CALLBACK+" "+messageCallbackReceiver);
        //log.info("DEBUG EVENT - "+SynapseDebugEventConstants.DEBUG_EVENT_CONTINUED_CALLBACK+" "+messageCallbackReceiver);
    }

    public void advertiseMediationFlowTerminatePoint(){
        setMessageContext(synCtx);
        //medFlowState=MediationFlowState.STOPPED;
        advertiseDebugEvent(SynapseDebugEventConstants.DEBUG_EVENT_TERMINATED+" "+msgReceiver+" "+msgCallbackReceiver);
        //log.info("DEBUG EVENT - "+SynapseDebugEventConstants.DEBUG_EVENT_TERMINATED+" "+msgReceiver+" - "+msgCallbackReceiver);
    }


    public void advertiseMediationFlowSkip(MessageContext synCtx, SynapseMediationFlowPoint skipPoint){
          //setMessageContext(synCtx);
          //medFlowState=MediationFlowState.SKIPPED;
          advertiseDebugEvent(SynapseDebugEventConstants.DEBUG_EVENT_PASSED_SKIPPOINT+ " " +skipPoint.getSynapseSequenceType().toString()+" "+skipPoint.getKey()+" "+toString(skipPoint.getMediatorPosition()));
          //log.info("DEBUG EVENT - "+SynapseDebugEventConstants.DEBUG_EVENT_PASSED_SKIPPOINT+ " " +skipPoint.getSynapseSequenceType().toString()+" "+skipPoint.getKey()+" "+toString(skipPoint.getMediatorPosition()));
    }


    public void advertiseMediationFlowBreakPoint(MessageContext synCtx, SynapseMediationFlowPoint breakPoint){
        try{
        setMessageContext(synCtx);
        medFlowState=MediationFlowState.SUSPENDED;
        advertiseDebugEvent(SynapseDebugEventConstants.DEBUG_EVENT_SUSPENDED_BREAKPOINT + " " +breakPoint.getSynapseSequenceType().toString()+" "+breakPoint.getKey()+" "+toString(breakPoint.getMediatorPosition()));
        //log.info("DEBUG EVENT - "+SynapseDebugEventConstants.DEBUG_EVENT_SUSPENDED_BREAKPOINT + " " +breakPoint.getSynapseSequenceType().toString()+" "+breakPoint.getKey()+" "+toString(breakPoint.getMediatorPosition()));
        String debug_line=null;
        while(medFlowState.equals(MediationFlowState.SUSPENDED)){
            if(debugInterface.getPortListenReader().ready()){
            debug_line=debugInterface.getPortListenReader().readLine();
            }
            if(debug_line!=null){
                processDebugCommand(debug_line);
                debug_line=null;
            }
        }
        advertiseDebugEvent(SynapseDebugEventConstants.DEBUG_EVENT_RESUMED_CLIENT);
        //log.info("DEBUG EVENT - "+SynapseDebugEventConstants.DEBUG_EVENT_RESUMED_CLIENT);
        }catch(IOException e){
            log.error("Error listening the command port at breakpoint hit");
        }
    }

   public void listenCommunicationChannel(){
        if(synEnv.isDebugEnabled()){
            try{
            String debug_line=null;
            if(debugInterface.getPortListenReader().ready()){
            debug_line=debugInterface.getPortListenReader().readLine();
            }
            if(debug_line!=null){
                processDebugCommand(debug_line);
                debug_line=null;
                if(debugInterface.getPortListenReader().ready()){
                debug_line=debugInterface.getPortListenReader().readLine();
                }
                while(debug_line!=null){
                    processDebugCommand(debug_line);
                    debug_line=null;
                    debug_line=debugInterface.getPortListenReader().readLine();
                }

            }else{
                log.info("No new line to read");
            }
            }catch(IOException ex){
                log.error("Error listening the command port");
            }
        }

    }
/*
   public void processDebugCommand(String debug_line){

        String[] command = debug_line.split("\\s+");
        if(command[0].equals(SynapseDebugCommandConstants.DEBUG_COMMAND_CLEAR)){
            int[] position=new int[command.length-4];
            String skipOrBreakPoint=command[1];
            String breakPointType=command[2];
            String seqKey=command[3];
            for(int counter=4;counter<command.length;counter++){
                position[counter-4]= Integer.parseInt(command[counter]);
            }
            if(skipOrBreakPoint.equals(SynapseDebugCommandConstants.DEBUG_COMMAND_BREAKPOINT)) {
                log.info("DEBUG COMMAND - "+SynapseDebugCommandConstants.DEBUG_COMMAND_CLEAR+" "+SynapseDebugCommandConstants.DEBUG_COMMAND_BREAKPOINT);
                this.unregisterMediationFlowBreakPoint(breakPointType, seqKey, position);
            }else if (skipOrBreakPoint.equals(SynapseDebugCommandConstants.DEBUG_COMMAND_SKIPPOINT)){
                log.info("DEBUG COMMAND - "+SynapseDebugCommandConstants.DEBUG_COMMAND_CLEAR+" "+SynapseDebugCommandConstants.DEBUG_COMMAND_SKIPPOINT);
                this.unregisterMediationFlowSkipPoint(breakPointType, seqKey, position);
            }
        }else if(command[0].equals(SynapseDebugCommandConstants.DEBUG_COMMAND_LOG)){
            String[] arguments=new String[command.length-1];
            for(int counter=1;counter<command.length;counter++){
                arguments[counter-1]= command[counter];
            }
            log.info("DEBUG COMMAND - "+SynapseDebugCommandConstants.DEBUG_COMMAND_LOG+" "+arguments[0]);
            this.debugLog(arguments);
        }else if(command[0].equals(SynapseDebugCommandConstants.DEBUG_COMMAND_RESUME)){
            log.info("DEBUG COMMAND - "+SynapseDebugCommandConstants.DEBUG_COMMAND_RESUME);
            this.debugResume();
        }else if(command[0].equals(SynapseDebugCommandConstants.DEBUG_COMMAND_SET)){
            int[] position=new int[command.length-4];
            String skipOrBreakPoint=command[1];
            String breakPointType=command[2];
            String seqKey=command[3];
            for(int counter=4;counter<command.length;counter++){
                position[counter-4]= Integer.parseInt(command[counter]);
            }
            if(skipOrBreakPoint.equals(SynapseDebugCommandConstants.DEBUG_COMMAND_BREAKPOINT)) {
                log.info("DEBUG COMMAND - "+SynapseDebugCommandConstants.DEBUG_COMMAND_SET+" "+SynapseDebugCommandConstants.DEBUG_COMMAND_BREAKPOINT);
                this.registerMediationFlowBreakPoint(breakPointType, seqKey, position);
            }else if (skipOrBreakPoint.equals(SynapseDebugCommandConstants.DEBUG_COMMAND_SKIPPOINT)){
                log.info("DEBUG COMMAND - "+SynapseDebugCommandConstants.DEBUG_COMMAND_SET+" "+SynapseDebugCommandConstants.DEBUG_COMMAND_SKIPPOINT);
                this.registerMediationFlowSkipPoint(breakPointType, seqKey, position);
            }
        }else
            System.out.println("command not found");
    } */

    public void processDebugCommand(String debug_line){
        try {
            JSONObject parsed_debug_line = new JSONObject(debug_line);
            String command="";
            if(parsed_debug_line.has(SynapseDebugCommandConstants.DEBUG_COMMAND)){
               command=parsed_debug_line.getString(SynapseDebugCommandConstants.DEBUG_COMMAND);
            }else{
                return;
            }
           //String[] command = debug_line.split("\\s+");
           if (command.equals(SynapseDebugCommandConstants.DEBUG_COMMAND_CLEAR)) {
                String skipOrBreakPointOrProperty=parsed_debug_line.getString(SynapseDebugCommandConstants.DEBUG_COMMAND_ARGUMENT);
               if(skipOrBreakPointOrProperty.equals(SynapseDebugCommandConstants.DEBUG_COMMAND_PROPERTY)){
                   String propertyContext=parsed_debug_line.getString(SynapseDebugCommandConstants.DEBUG_COMMAND_PROPERTY_CONTEXT);
                   JSONObject property_arguments = parsed_debug_line.getJSONObject(SynapseDebugCommandConstants.DEBUG_COMMAND_PROPERTY);
                   this.addMediationFlowPointProperty(propertyContext,property_arguments,false);
               }else {
                   String mediation_component = parsed_debug_line.getString(SynapseDebugCommandConstants.DEBUG_COMMAND_MEDIATION_COMPONENT);
                   JSONObject med_component_arguments = parsed_debug_line.getJSONObject(mediation_component);

                   if (skipOrBreakPointOrProperty.equals(SynapseDebugCommandConstants.DEBUG_COMMAND_BREAKPOINT)) {
                       //log.info("DEBUG COMMAND - " + SynapseDebugCommandConstants.DEBUG_COMMAND_CLEAR + " " + SynapseDebugCommandConstants.DEBUG_COMMAND_BREAKPOINT);
                       this.registerMediationFlowPoint(mediation_component, med_component_arguments, true, false);
                   } else if (skipOrBreakPointOrProperty.equals(SynapseDebugCommandConstants.DEBUG_COMMAND_SKIP)) {
                       // log.info("DEBUG COMMAND - " + SynapseDebugCommandConstants.DEBUG_COMMAND_CLEAR + " " + SynapseDebugCommandConstants.DEBUG_COMMAND_SKIP);
                       this.registerMediationFlowPoint(mediation_component, med_component_arguments, false, false);
                   }
               }
            } else if (command.equals(SynapseDebugCommandConstants.DEBUG_COMMAND_GET)) {
               String propertyOrProperties=parsed_debug_line.getString(SynapseDebugCommandConstants.DEBUG_COMMAND_ARGUMENT);
               String propertyContext=parsed_debug_line.getString(SynapseDebugCommandConstants.DEBUG_COMMAND_PROPERTY_CONTEXT);
               JSONObject property_arguments=null;
               if(propertyOrProperties.equals(SynapseDebugCommandConstants.DEBUG_COMMAND_PROPERTY)) {
                  property_arguments = parsed_debug_line.getJSONObject(SynapseDebugCommandConstants.DEBUG_COMMAND_PROPERTY);
               }

                //log.info("DEBUG COMMAND - " + SynapseDebugCommandConstants.DEBUG_COMMAND_LOG + " " + arguments[0]);
                this.acquireMediationFlowPointProperties(propertyOrProperties,propertyContext, property_arguments);
            } else if (command.equals(SynapseDebugCommandConstants.DEBUG_COMMAND_RESUME)) {
                //log.info("DEBUG COMMAND - " + SynapseDebugCommandConstants.DEBUG_COMMAND_RESUME);
                this.debugResume();
            } else if (command.equals(SynapseDebugCommandConstants.DEBUG_COMMAND_SET)) {
               String skipOrBreakPointOrProperty=parsed_debug_line.getString(SynapseDebugCommandConstants.DEBUG_COMMAND_ARGUMENT);
               if(skipOrBreakPointOrProperty.equals(SynapseDebugCommandConstants.DEBUG_COMMAND_PROPERTY)){
                   String propertyContext=parsed_debug_line.getString(SynapseDebugCommandConstants.DEBUG_COMMAND_PROPERTY_CONTEXT);
                   JSONObject property_arguments = parsed_debug_line.getJSONObject(SynapseDebugCommandConstants.DEBUG_COMMAND_PROPERTY);
                   this.addMediationFlowPointProperty(propertyContext,property_arguments,true);
               }else {
                   String mediation_component = parsed_debug_line.getString(SynapseDebugCommandConstants.DEBUG_COMMAND_MEDIATION_COMPONENT);
                   JSONObject med_component_arguments = parsed_debug_line.getJSONObject(mediation_component);
                   if (skipOrBreakPointOrProperty.equals(SynapseDebugCommandConstants.DEBUG_COMMAND_BREAKPOINT)) {
                       //  log.info("DEBUG COMMAND - " + SynapseDebugCommandConstants.DEBUG_COMMAND_SET + " " + SynapseDebugCommandConstants.DEBUG_COMMAND_BREAKPOINT);
                       this.registerMediationFlowPoint(mediation_component, med_component_arguments, true, true);
                   } else if (skipOrBreakPointOrProperty.equals(SynapseDebugCommandConstants.DEBUG_COMMAND_SKIP)) {
                       // log.info("DEBUG COMMAND - " + SynapseDebugCommandConstants.DEBUG_COMMAND_SET + " " + SynapseDebugCommandConstants.DEBUG_COMMAND_SKIP);
                       this.registerMediationFlowPoint(mediation_component, med_component_arguments, false, true);
                   }
               }
            } else
                  log.error("command not found");
        }catch (JSONException ex) {
            log.error("Unable to process debug command");
        }
    }

    public void registerMediationFlowPoint( String mediation_component,JSONObject med_component_arguments,boolean isBreakpoint,boolean registerMode){
        try {
            if (mediation_component.equals(SynapseDebugCommandConstants.DEBUG_COMMAND_MEDIATION_COMPONENT_CONNECTOR)) {
                String connector_key = med_component_arguments.getString(SynapseDebugCommandConstants.DEBUG_COMMAND_MEDIATION_COMPONENT_CONNECTOR_KEY);
                String connector_method_name = med_component_arguments.getString(SynapseDebugCommandConstants.DEBUG_COMMAND_MEDIATION_COMPONENT_CONNECTOR_METHOD);
                String component_mediator_position=med_component_arguments.getString(SynapseDebugCommandConstants.DEBUG_COMMAND_MEDIATION_COMPONENT_MEDIATOR_POSITION);
                String[] mediator_position_array = component_mediator_position.split("\\s+");
                int[] med_pos=new int[mediator_position_array.length];
                for(int counter=0;counter<mediator_position_array.length;counter++){
                    med_pos[counter]=Integer.valueOf(mediator_position_array[counter]);
                }
                //String template_key=SynapseDebugManagerConstants.CONNECTOR_PACKAGE+"."+connector_key+"."+connector_method_name;
               // Mediator mediation_component_template=synCfg.getSequenceTemplate(template_key);
                if(isBreakpoint){
                    registerConnectorMediationFlowBreakPoint(connector_key,connector_method_name,med_pos,registerMode);
                }else{
                    registerConnectorMediationFlowSkip(connector_key,connector_method_name,med_pos,registerMode);
                }

            } else if (mediation_component.equals(SynapseDebugCommandConstants.DEBUG_COMMAND_MEDIATION_COMPONENT_SEQUENCE)) {
                if((!med_component_arguments.has(SynapseDebugCommandConstants.DEBUG_COMMAND_MEDIATION_COMPONENT_SEQUENCE_PROXY))&&(!med_component_arguments.has(SynapseDebugCommandConstants.DEBUG_COMMAND_MEDIATION_COMPONENT_SEQUENCE_API)))
                {

                    String sequence_key = med_component_arguments.getString(SynapseDebugCommandConstants.DEBUG_COMMAND_MEDIATION_COMPONENT_SEQUENCE_KEY);
                    String sequence_type = med_component_arguments.getString(SynapseDebugCommandConstants.DEBUG_COMMAND_MEDIATION_COMPONENT_SEQUENCE_TYPE);
                    String component_mediator_position = med_component_arguments.getString(SynapseDebugCommandConstants.DEBUG_COMMAND_MEDIATION_COMPONENT_MEDIATOR_POSITION);
                    String[] mediator_position_array = component_mediator_position.split("\\s+");
                    int[] med_pos = new int[mediator_position_array.length];
                    for (int counter = 0; counter < mediator_position_array.length; counter++) {
                        med_pos[counter] = Integer.valueOf(mediator_position_array[counter]);
                    }
                    if (isBreakpoint) {
                        registerSequenceMediationFlowBreakPoint(sequence_type, sequence_key, med_pos, registerMode);
                    } else {
                        registerSequenceMediationFlowSkip(sequence_type, sequence_key, med_pos, registerMode);
                    }
                }else if (med_component_arguments.has(SynapseDebugCommandConstants.DEBUG_COMMAND_MEDIATION_COMPONENT_SEQUENCE_PROXY)){
                    JSONObject proxy_arguments=med_component_arguments.getJSONObject(SynapseDebugCommandConstants.DEBUG_COMMAND_MEDIATION_COMPONENT_SEQUENCE_PROXY);
                    String proxy_key=proxy_arguments.getString(SynapseDebugCommandConstants.DEBUG_COMMAND_MEDIATION_COMPONENT_SEQUENCE_PROXY_KEY);
                    String sequence_type = proxy_arguments.getString(SynapseDebugCommandConstants.DEBUG_COMMAND_MEDIATION_COMPONENT_SEQUENCE_TYPE);
                    String component_mediator_position = proxy_arguments.getString(SynapseDebugCommandConstants.DEBUG_COMMAND_MEDIATION_COMPONENT_MEDIATOR_POSITION);
                    String[] mediator_position_array = component_mediator_position.split("\\s+");
                    int[] med_pos = new int[mediator_position_array.length];
                    for (int counter = 0; counter < mediator_position_array.length; counter++) {
                        med_pos[counter] = Integer.valueOf(mediator_position_array[counter]);
                    }
                    if (isBreakpoint) {
                        registerProxySequenceMediationFlowBreakPoint(sequence_type, proxy_key, med_pos,registerMode);
                    } else {
                        registerProxySequenceMediationFlowSkip(sequence_type, proxy_key, med_pos, registerMode);
                    }

                }else if (med_component_arguments.has(SynapseDebugCommandConstants.DEBUG_COMMAND_MEDIATION_COMPONENT_SEQUENCE_API_KEY)){
                    JSONObject api_arguments=med_component_arguments.getJSONObject(SynapseDebugCommandConstants.DEBUG_COMMAND_MEDIATION_COMPONENT_SEQUENCE_API);
                    JSONObject resource_arguments=api_arguments.getJSONObject(SynapseDebugCommandConstants.DEBUG_COMMAND_MEDIATION_COMPONENT_SEQUENCE_API_RESOURCE);
                    String mapping=null;
                    if(resource_arguments.has(SynapseDebugCommandConstants.DEBUG_COMMAND_MEDIATION_COMPONENT_SEQUENCE_API_RESOURCE_URI_TEMPLATE)){
                        mapping=resource_arguments.getString(SynapseDebugCommandConstants.DEBUG_COMMAND_MEDIATION_COMPONENT_SEQUENCE_API_RESOURCE_URI_TEMPLATE);
                    }else if (resource_arguments.has(SynapseDebugCommandConstants.DEBUG_COMMAND_MEDIATION_COMPONENT_SEQUENCE_API_RESOURCE_URL_MAPPING)){
                        mapping=resource_arguments.getString(SynapseDebugCommandConstants.DEBUG_COMMAND_MEDIATION_COMPONENT_SEQUENCE_API_RESOURCE_URL_MAPPING);
                    }
                    String method=resource_arguments.getString(SynapseDebugCommandConstants.DEBUG_COMMAND_MEDIATION_COMPONENT_SEQUENCE_API_RESOURCE_METHOD);
                    String api_key=api_arguments.getString(SynapseDebugCommandConstants.DEBUG_COMMAND_MEDIATION_COMPONENT_SEQUENCE_API_KEY);
                    String sequence_type = api_arguments.getString(SynapseDebugCommandConstants.DEBUG_COMMAND_MEDIATION_COMPONENT_SEQUENCE_TYPE);
                    String component_mediator_position = api_arguments.getString(SynapseDebugCommandConstants.DEBUG_COMMAND_MEDIATION_COMPONENT_MEDIATOR_POSITION);
                    String[] mediator_position_array = component_mediator_position.split("\\s+");
                    int[] med_pos = new int[mediator_position_array.length];
                    for (int counter = 0; counter < mediator_position_array.length; counter++) {
                        med_pos[counter] = Integer.valueOf(mediator_position_array[counter]);
                    }
                    if (isBreakpoint) {
                        registerAPISequenceMediationFlowBreakPoint(mapping,method,sequence_type, api_key, med_pos,registerMode);
                    } else {
                        registerAPISequenceMediationFlowSkip(mapping,method,sequence_type, api_key, med_pos, registerMode);
                    }
                }

            } else if (mediation_component.equals(SynapseDebugCommandConstants.DEBUG_COMMAND_MEDIATION_COMPONENT_TEMPLATE)) {
                String template_key = med_component_arguments.getString(SynapseDebugCommandConstants.DEBUG_COMMAND_MEDIATION_COMPONENT_TEMPLATE_KEY);
                String component_mediator_position=med_component_arguments.getString(SynapseDebugCommandConstants.DEBUG_COMMAND_MEDIATION_COMPONENT_MEDIATOR_POSITION);
                String[] mediator_position_array = component_mediator_position.split("\\s+");
                int[] med_pos=new int[mediator_position_array.length];
                for(int counter=0;counter<mediator_position_array.length;counter++){
                    med_pos[counter]=Integer.valueOf(mediator_position_array[counter]);
                }
                //Mediator mediation_component_template=synCfg.getSequenceTemplate(template_key);
                if(isBreakpoint){
                    registerTemplateMediationFlowBreakPoint(template_key,med_pos,registerMode);
                }else{
                    registerTemplateMediationFlowSkip(template_key,med_pos,registerMode);
                }
            }


        }catch (JSONException ex){
            log.error("Unable to register mediation flow point");
        }
    }


    public void registerAPISequenceMediationFlowSkip(String mapping, String method, String seqType,String apiKey,int[] position,boolean registerMode){
        SynapseSequenceType synapseSequenceType = SynapseSequenceType.valueOf(seqType);
        SynapseMediationFlowPoint skipPoint=new SynapseMediationFlowPoint();
        skipPoint.setSynapseMediationComponent(SynapseMediationComponent.SEQUENCE);
        skipPoint.setKey(apiKey);
        skipPoint.setMediatorPosition(position);
        skipPoint.setSynapseSequenceType(synapseSequenceType);
        skipPoint.setSequenceMediationComponentIdentifier(SynapseDebugCommandConstants.DEBUG_COMMAND_MEDIATION_COMPONENT_SEQUENCE_API);
        skipPoint.setAPIIdentifierMapping(mapping);
        skipPoint.setAPIIdentifierMethod(method);
        Mediator seqMediator=null;
        Resource api_resource=null;
        Resource[] resource_array=synCfg.getAPI(apiKey).getResources();
        for (int counter=0;counter<resource_array.length;counter ++){
            if(mapping.equals(resource_array[counter].getDispatcherHelper().getString())){
                for(String m1 : resource_array[counter].getMethods()){
                    if(m1.equals(method)){
                        api_resource=resource_array[counter];
                        break;
                    }
                }
                if(api_resource!=null){
                    break;
                }
            }
        }
        if(api_resource!=null) {
            if (synapseSequenceType.equals(SynapseSequenceType.API_INSEQ)) {
                seqMediator = api_resource.getInSequence();
            } else if (synapseSequenceType.equals(SynapseSequenceType.API_OUTSEQ)) {
                seqMediator = api_resource.getOutSequence();
            } else if (synapseSequenceType.equals(SynapseSequenceType.API_FAULTSEQ)) {
                seqMediator = api_resource.getFaultSequence();
            }
        }else{

            log.error("No resource found");
            return;
        }
        if(seqMediator!=null) {
            Mediator current_mediator = null;
            for (int counter = 0; counter < position.length; counter++) {
                if(counter==0){
                    current_mediator = ((AbstractListMediator) seqMediator).getChild(position[counter]);
                }
                if(current_mediator!=null) {
                    current_mediator = ((AbstractListMediator) current_mediator).getChild(position[counter]);
                }
            }
            if(current_mediator!=null) {
                skipPoint.setMediatorReference(current_mediator);
                if(registerMode) {
                    if (!((AbstractMediator) current_mediator).isSkipEnabled()) {
                        ((AbstractMediator) current_mediator).setSkipEnabled(true);
                        ((AbstractMediator) current_mediator).registerMediationFlowPoint(skipPoint);
                        // log.info("REGISTERED SKIPPOINT - " + skipPoint.getSynapseSequenceType().toString() + " " + skipPoint.getKey() + " " + toString(skipPoint.getMediatorPosition()));
                        debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_SUCCESS);
                        debugInterface.getPortListenWriter().flush();
                    } else {
                        log.error("Already skip enabled");
                        // log.info("FAILED REGISTER SKIPPOINT - Already skip enabled " + skipPoint.getSynapseSequenceType().toString() + " " + skipPoint.getKey() + " " + toString(skipPoint.getMediatorPosition()));
                        debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_FAILED);
                        debugInterface.getPortListenWriter().flush();
                    }
                }else{
                    if(((AbstractMediator) current_mediator).isSkipEnabled()) {
                        ((AbstractMediator) current_mediator).setSkipEnabled(false);
                        ((AbstractMediator) current_mediator).unregisterMediationFlowPoint();

                        //  log.info("UN REGISTERED SKIPPOINT - " + skipPoint.getSynapseSequenceType().toString() + " " + skipPoint.getKey() + " " + toString(skipPoint.getMediatorPosition()));
                        debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_SUCCESS);
                        debugInterface.getPortListenWriter().flush();
                    }else{
                        log.error("Already skip disabled");
                        //  log.info("FAILED REGISTER SKIPPOINT - Already skip disabled "+skipPoint.getSynapseSequenceType().toString() + " " + skipPoint.getKey() + " " + toString(skipPoint.getMediatorPosition()));
                        debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_FAILED);
                        debugInterface.getPortListenWriter().flush();
                    }

                }
            }else {
                if(registerMode) {
                    log.error("Non existing mediator position");
                    //log.info("FAILED REGISTER SKIPPOINT - Non existing mediator position "+skipPoint.getSynapseSequenceType().toString() + " " + skipPoint.getKey() + " " + toString(skipPoint.getMediatorPosition()));
                    debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_FAILED);
                    debugInterface.getPortListenWriter().flush();
                }else{
                    log.error("Non existing mediator position");
                    // log.info("FAILED UN REGISTER SKIPPOINT - Non existing mediator position "+skipPoint.getSynapseSequenceType().toString() + " " + skipPoint.getKey() + " " + toString(skipPoint.getMediatorPosition()));
                    debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_FAILED);
                    debugInterface.getPortListenWriter().flush();
                }
            }
        }else{
            if(registerMode) {
                log.error("Non existing sequence");
                //log.info("FAILED REGISTER SKIPPOINT - Non existing sequence "+skipPoint.getSynapseSequenceType().toString()+" "+skipPoint.getKey());
                debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_FAILED);
                debugInterface.getPortListenWriter().flush();
            }else {
                log.error("Non existing sequence");
                //log.info("FAILED UN REGISTER SKIPPOINT - Non existing sequence "+skipPoint.getSynapseSequenceType().toString()+" "+skipPoint.getKey()+" "+toString(skipPoint.getMediatorPosition()));
                debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_FAILED);
                debugInterface.getPortListenWriter().flush();
            }
        }
    }



   public void registerAPISequenceMediationFlowBreakPoint(String mapping, String method,String sequenceType,String apiKey,int[] position,boolean registerMode){
        SynapseSequenceType synapseSequenceType = SynapseSequenceType.valueOf(sequenceType);
        SynapseMediationFlowPoint breakPoint=new SynapseMediationFlowPoint();
        breakPoint.setSynapseMediationComponent(SynapseMediationComponent.SEQUENCE);
        breakPoint.setKey(apiKey);
        breakPoint.setMediatorPosition(position);
        breakPoint.setSynapseSequenceType(synapseSequenceType);
        breakPoint.setSequenceMediationComponentIdentifier(SynapseDebugCommandConstants.DEBUG_COMMAND_MEDIATION_COMPONENT_SEQUENCE_API);
        breakPoint.setAPIIdentifierMapping(mapping);
        breakPoint.setAPIIdentifierMethod(method);
        Mediator seqMediator=null;
        Resource api_resource=null;
        Resource[] resource_array=synCfg.getAPI(apiKey).getResources();
        for (int counter=0;counter<resource_array.length;counter ++){
            if(mapping.equals(resource_array[counter].getDispatcherHelper().getString())){
                  for(String m1 : resource_array[counter].getMethods()){
                      if(m1.equals(method)){
                          api_resource=resource_array[counter];
                          break;
                      }
                  }
                  if(api_resource!=null){
                      break;
                  }
            }
        }
        if(api_resource!=null) {
            if (synapseSequenceType.equals(SynapseSequenceType.API_INSEQ)) {
                seqMediator = api_resource.getInSequence();
            } else if (synapseSequenceType.equals(SynapseSequenceType.API_OUTSEQ)) {
                seqMediator = api_resource.getOutSequence();
            } else if (synapseSequenceType.equals(SynapseSequenceType.API_FAULTSEQ)) {
                seqMediator = api_resource.getFaultSequence();
            }
        }else{
            log.error("Resource not found");
            return;
        }
        if(seqMediator!=null) {
            Mediator current_mediator = null;
            for (int counter = 0; counter < position.length; counter++) {
                if(counter==0){
                    current_mediator = ((AbstractListMediator) seqMediator).getChild(position[counter]);
                }
                if(current_mediator!=null) {
                    current_mediator = ((AbstractListMediator) current_mediator).getChild(position[counter]);
                }
            }
            if(current_mediator!=null) {
                breakPoint.setMediatorReference(current_mediator);
                if(registerMode) {
                    if (!((AbstractMediator) current_mediator).isBreakPoint()) {
                        ((AbstractMediator) current_mediator).setBreakPoint(true);
                        ((AbstractMediator) current_mediator).registerMediationFlowPoint(breakPoint);
                        //    log.info("REGISTERED BREAKPOINT - " + breakPoint.getSynapseSequenceType().toString() + " " + breakPoint.getKey() + " " + toString(breakPoint.getMediatorPosition()));
                        debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_SUCCESS);
                        debugInterface.getPortListenWriter().flush();
                    } else {
                        log.error("Already breakpoint enabled");
                        //      log.info("FAILED REGISTER SKIPPOINT - Already breakpoint enabled "+breakPoint.getSynapseSequenceType().toString() + " " + breakPoint.getKey() + " " + toString(breakPoint.getMediatorPosition()));
                        debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_FAILED);
                        debugInterface.getPortListenWriter().flush();
                    }
                }else{
                    if(((AbstractMediator)current_mediator).isBreakPoint()) {
                        ((AbstractMediator) current_mediator).setBreakPoint(false);
                        ((AbstractMediator) current_mediator).unregisterMediationFlowPoint();
                        //     log.info("UN REGISTERED BREAKPOINT - " + breakPoint.getSynapseSequenceType().toString() + " " + breakPoint.getKey() + " " + toString(breakPoint.getMediatorPosition()));
                        debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_SUCCESS);
                        debugInterface.getPortListenWriter().flush();
                    }else{
                        log.error("Already breakpoint disabled");
                        //     log.info("FAILED REGISTER SKIPPOINT - Already breakpoint disabled "+breakPoint.getSynapseSequenceType().toString() + " " + breakPoint.getKey() + " " + toString(breakPoint.getMediatorPosition()));
                        debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_FAILED);
                        debugInterface.getPortListenWriter().flush();
                    }

                }
            }else {
                if(registerMode) {
                    log.error("Non existing mediator position");
                    //   log.info("FAILED REGISTER BREAKPOINT - Non existing mediator position "+breakPoint.getSynapseSequenceType().toString() + " " + breakPoint.getKey() + " " + toString(breakPoint.getMediatorPosition()));
                    debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_FAILED);
                    debugInterface.getPortListenWriter().flush();
                }else{
                    log.error("Non existing mediator position");
                    //  log.info("FAILED UN REGISTER BREAKPOINT - Non existing mediator position "+breakPoint.getSynapseSequenceType().toString() + " " + breakPoint.getKey() + " " + toString(breakPoint.getMediatorPosition()));
                    debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_FAILED);
                    debugInterface.getPortListenWriter().flush();

                }
            }
        }else{
            if(registerMode) {
                log.error("Non existing sequence");
                // log.info("FAILED REGISTER BREAKPOINT - Non existing sequence "+breakPoint.getSynapseSequenceType().toString()+" "+breakPoint.getKey());
                debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_FAILED);
                debugInterface.getPortListenWriter().flush();
            }else{
                log.error("Non existing sequence");
                // log.info("FAILED UN REGISTER BREAKPOINT - Non existing sequence "+breakPoint.getSynapseSequenceType().toString()+" "+breakPoint.getKey());
                debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_FAILED);
                debugInterface.getPortListenWriter().flush();

            }
        }
    }







    public void registerProxySequenceMediationFlowBreakPoint(String sequenceType,String proxyKey,int[] position,boolean registerMode){
        SynapseSequenceType synapseSequenceType = SynapseSequenceType.valueOf(sequenceType);
        SynapseMediationFlowPoint breakPoint=new SynapseMediationFlowPoint();
        breakPoint.setSynapseMediationComponent(SynapseMediationComponent.SEQUENCE);
        breakPoint.setKey(proxyKey);
        breakPoint.setMediatorPosition(position);
        breakPoint.setSequenceMediationComponentIdentifier(SynapseDebugCommandConstants.DEBUG_COMMAND_MEDIATION_COMPONENT_SEQUENCE_PROXY);
        breakPoint.setSynapseSequenceType(synapseSequenceType);
        Mediator seqMediator=null;
        ProxyService proxy=null;
        proxy=synCfg.getProxyService(proxyKey);
        if(proxy!=null) {
            if (synapseSequenceType.equals(SynapseSequenceType.PROXY_INSEQ)) {
                seqMediator = proxy.getTargetInLineInSequence();
            } else if (synapseSequenceType.equals(SynapseSequenceType.PROXY_OUTSEQ)) {
                seqMediator = proxy.getTargetInLineOutSequence();
            } else if (synapseSequenceType.equals(SynapseSequenceType.PROXY_FAULTSEQ)) {
                seqMediator = proxy.getTargetInLineFaultSequence();
            }
        }else{
            return;
        }

        if(seqMediator!=null) {
            Mediator current_mediator = null;
            for (int counter = 0; counter < position.length; counter++) {
                if(counter==0){
                    current_mediator = ((AbstractListMediator) seqMediator).getChild(position[counter]);
                }
                if(current_mediator!=null) {
                    current_mediator = ((AbstractListMediator) current_mediator).getChild(position[counter]);
                }
            }
            if(current_mediator!=null) {
                breakPoint.setMediatorReference(current_mediator);
                if(registerMode) {
                    if (!((AbstractMediator) current_mediator).isBreakPoint()) {
                        ((AbstractMediator) current_mediator).setBreakPoint(true);
                        ((AbstractMediator) current_mediator).registerMediationFlowPoint(breakPoint);
                        //    log.info("REGISTERED BREAKPOINT - " + breakPoint.getSynapseSequenceType().toString() + " " + breakPoint.getKey() + " " + toString(breakPoint.getMediatorPosition()));
                        debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_SUCCESS);
                        debugInterface.getPortListenWriter().flush();
                    } else {
                        //      log.info("FAILED REGISTER SKIPPOINT - Already breakpoint enabled "+breakPoint.getSynapseSequenceType().toString() + " " + breakPoint.getKey() + " " + toString(breakPoint.getMediatorPosition()));
                        debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_FAILED);
                        debugInterface.getPortListenWriter().flush();
                    }
                }else{
                    if(((AbstractMediator)current_mediator).isBreakPoint()) {
                        ((AbstractMediator) current_mediator).setBreakPoint(false);
                        ((AbstractMediator) current_mediator).unregisterMediationFlowPoint();
                        //     log.info("UN REGISTERED BREAKPOINT - " + breakPoint.getSynapseSequenceType().toString() + " " + breakPoint.getKey() + " " + toString(breakPoint.getMediatorPosition()));
                        debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_SUCCESS);
                        debugInterface.getPortListenWriter().flush();
                    }else{
                        //     log.info("FAILED REGISTER SKIPPOINT - Already breakpoint disabled "+breakPoint.getSynapseSequenceType().toString() + " " + breakPoint.getKey() + " " + toString(breakPoint.getMediatorPosition()));
                        debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_FAILED);
                        debugInterface.getPortListenWriter().flush();
                    }

                }
            }else {
                if(registerMode) {
                    //   log.info("FAILED REGISTER BREAKPOINT - Non existing mediator position "+breakPoint.getSynapseSequenceType().toString() + " " + breakPoint.getKey() + " " + toString(breakPoint.getMediatorPosition()));
                    debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_FAILED);
                    debugInterface.getPortListenWriter().flush();
                }else{
                    //  log.info("FAILED UN REGISTER BREAKPOINT - Non existing mediator position "+breakPoint.getSynapseSequenceType().toString() + " " + breakPoint.getKey() + " " + toString(breakPoint.getMediatorPosition()));
                    debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_FAILED);
                    debugInterface.getPortListenWriter().flush();

                }
            }
        }else{
            if(registerMode) {
                // log.info("FAILED REGISTER BREAKPOINT - Non existing sequence "+breakPoint.getSynapseSequenceType().toString()+" "+breakPoint.getKey());
                debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_FAILED);
                debugInterface.getPortListenWriter().flush();
            }else{
                // log.info("FAILED UN REGISTER BREAKPOINT - Non existing sequence "+breakPoint.getSynapseSequenceType().toString()+" "+breakPoint.getKey());
                debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_FAILED);
                debugInterface.getPortListenWriter().flush();

            }
        }
    }

    public void registerProxySequenceMediationFlowSkip(String seqType,String proxyKey,int[] position,boolean registerMode){
        SynapseSequenceType synapseSequenceType = SynapseSequenceType.valueOf(seqType);
        SynapseMediationFlowPoint skipPoint=new SynapseMediationFlowPoint();
        skipPoint.setSynapseMediationComponent(SynapseMediationComponent.SEQUENCE);
        skipPoint.setKey(proxyKey);
        skipPoint.setMediatorPosition(position);
        skipPoint.setSequenceMediationComponentIdentifier(SynapseDebugCommandConstants.DEBUG_COMMAND_MEDIATION_COMPONENT_SEQUENCE_PROXY);
        skipPoint.setSynapseSequenceType(synapseSequenceType);
        Mediator seqMediator=null;
        ProxyService proxy=null;
        proxy=synCfg.getProxyService(proxyKey);
        if(proxy!=null) {
            if (synapseSequenceType.equals(SynapseSequenceType.PROXY_INSEQ)) {
                seqMediator = proxy.getTargetInLineInSequence();
            } else if (synapseSequenceType.equals(SynapseSequenceType.PROXY_OUTSEQ)) {
                seqMediator = proxy.getTargetInLineOutSequence();
            } else if (synapseSequenceType.equals(SynapseSequenceType.PROXY_FAULTSEQ)) {
                seqMediator = proxy.getTargetInLineFaultSequence();
            }
        }else{
            return;
        }
        if(seqMediator!=null) {
            Mediator current_mediator = null;
            for (int counter = 0; counter < position.length; counter++) {
                if(counter==0){
                    current_mediator = ((AbstractListMediator) seqMediator).getChild(position[counter]);
                }
                if(current_mediator!=null) {
                    current_mediator = ((AbstractListMediator) current_mediator).getChild(position[counter]);
                }
            }
            if(current_mediator!=null) {
                skipPoint.setMediatorReference(current_mediator);
                if(registerMode) {
                    if (!((AbstractMediator) current_mediator).isSkipEnabled()) {
                        ((AbstractMediator) current_mediator).setSkipEnabled(true);
                        ((AbstractMediator) current_mediator).registerMediationFlowPoint(skipPoint);
                        // log.info("REGISTERED SKIPPOINT - " + skipPoint.getSynapseSequenceType().toString() + " " + skipPoint.getKey() + " " + toString(skipPoint.getMediatorPosition()));
                        debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_SUCCESS);
                        debugInterface.getPortListenWriter().flush();
                    } else {
                        // log.info("FAILED REGISTER SKIPPOINT - Already skip enabled " + skipPoint.getSynapseSequenceType().toString() + " " + skipPoint.getKey() + " " + toString(skipPoint.getMediatorPosition()));
                        debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_FAILED);
                        debugInterface.getPortListenWriter().flush();
                    }
                }else{
                    if(((AbstractMediator) current_mediator).isSkipEnabled()) {
                        ((AbstractMediator) current_mediator).setSkipEnabled(false);
                        ((AbstractMediator) current_mediator).unregisterMediationFlowPoint();
                        //  log.info("UN REGISTERED SKIPPOINT - " + skipPoint.getSynapseSequenceType().toString() + " " + skipPoint.getKey() + " " + toString(skipPoint.getMediatorPosition()));
                        debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_SUCCESS);
                        debugInterface.getPortListenWriter().flush();
                    }else{
                        //  log.info("FAILED REGISTER SKIPPOINT - Already skip disabled "+skipPoint.getSynapseSequenceType().toString() + " " + skipPoint.getKey() + " " + toString(skipPoint.getMediatorPosition()));
                        debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_FAILED);
                        debugInterface.getPortListenWriter().flush();
                    }

                }
            }else {
                if(registerMode) {
                    //log.info("FAILED REGISTER SKIPPOINT - Non existing mediator position "+skipPoint.getSynapseSequenceType().toString() + " " + skipPoint.getKey() + " " + toString(skipPoint.getMediatorPosition()));
                    debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_FAILED);
                    debugInterface.getPortListenWriter().flush();
                }else{
                    // log.info("FAILED UN REGISTER SKIPPOINT - Non existing mediator position "+skipPoint.getSynapseSequenceType().toString() + " " + skipPoint.getKey() + " " + toString(skipPoint.getMediatorPosition()));
                    debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_FAILED);
                    debugInterface.getPortListenWriter().flush();
                }
            }
        }else{
            if(registerMode) {
                //log.info("FAILED REGISTER SKIPPOINT - Non existing sequence "+skipPoint.getSynapseSequenceType().toString()+" "+skipPoint.getKey());
                debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_FAILED);
                debugInterface.getPortListenWriter().flush();
            }else {
                //log.info("FAILED UN REGISTER SKIPPOINT - Non existing sequence "+skipPoint.getSynapseSequenceType().toString()+" "+skipPoint.getKey()+" "+toString(skipPoint.getMediatorPosition()));
                debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_FAILED);
                debugInterface.getPortListenWriter().flush();
            }
        }
    }




    public void registerConnectorMediationFlowBreakPoint(String connectorKey,String connectorMethod,int[] position,boolean registerMode){
        SynapseMediationFlowPoint breakPoint=new SynapseMediationFlowPoint();
        breakPoint.setSynapseMediationComponent(SynapseMediationComponent.CONNECTOR);
        breakPoint.setKey(connectorKey);
        breakPoint.setMediatorPosition(position);
        Mediator templateMediator=null;
        String template_key=SynapseDebugManagerConstants.CONNECTOR_PACKAGE+"."+connectorKey+"."+connectorMethod;
        templateMediator=synCfg.getSequenceTemplate(template_key);
        if(templateMediator!=null) {
            Mediator current_mediator = null;
            for (int counter = 0; counter < position.length; counter++) {
                if(counter==0){
                    current_mediator = ((AbstractListMediator) templateMediator).getChild(position[counter]);
                }
                if(current_mediator!=null) {
                    current_mediator = ((AbstractListMediator) current_mediator).getChild(position[counter]);
                }
            }
            if(current_mediator!=null) {
                breakPoint.setMediatorReference(current_mediator);
                if(registerMode) {
                    if (!((AbstractMediator) current_mediator).isBreakPoint()) {
                        ((AbstractMediator) current_mediator).setBreakPoint(true);
                        ((AbstractMediator) current_mediator).registerMediationFlowPoint(breakPoint);
                        //    log.info("REGISTERED BREAKPOINT - " + breakPoint.getSynapseSequenceType().toString() + " " + breakPoint.getKey() + " " + toString(breakPoint.getMediatorPosition()));
                        debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_SUCCESS);
                        debugInterface.getPortListenWriter().flush();
                    } else {
                        //      log.info("FAILED REGISTER SKIPPOINT - Already breakpoint enabled "+breakPoint.getSynapseSequenceType().toString() + " " + breakPoint.getKey() + " " + toString(breakPoint.getMediatorPosition()));
                        debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_FAILED);
                        debugInterface.getPortListenWriter().flush();
                    }
                }else{
                    if(((AbstractMediator)current_mediator).isBreakPoint()) {
                        ((AbstractMediator) current_mediator).setBreakPoint(false);
                        ((AbstractMediator) current_mediator).unregisterMediationFlowPoint();
                        //log.info("UN REGISTERED BREAKPOINT - " + breakPoint.getSynapseSequenceType().toString() + " " + breakPoint.getKey() + " " + toString(breakPoint.getMediatorPosition()));
                        debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_SUCCESS);
                        debugInterface.getPortListenWriter().flush();
                    }else{
                        //log.info("FAILED REGISTER SKIPPOINT - Already breakpoint disabled "+breakPoint.getSynapseSequenceType().toString() + " " + breakPoint.getKey() + " " + toString(breakPoint.getMediatorPosition()));
                        debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_FAILED);
                        debugInterface.getPortListenWriter().flush();
                    }

                }
            }else {
                if(registerMode) {
                    //   log.info("FAILED REGISTER BREAKPOINT - Non existing mediator position "+breakPoint.getSynapseSequenceType().toString() + " " + breakPoint.getKey() + " " + toString(breakPoint.getMediatorPosition()));
                    debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_FAILED);
                    debugInterface.getPortListenWriter().flush();
                }else{
                   // log.info("FAILED UN REGISTER BREAKPOINT - Non existing mediator position "+breakPoint.getSynapseSequenceType().toString() + " " + breakPoint.getKey() + " " + toString(breakPoint.getMediatorPosition()));
                    debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_FAILED);
                    debugInterface.getPortListenWriter().flush();

                }
            }
        }else{
            if(registerMode) {
                // log.info("FAILED REGISTER BREAKPOINT - Non existing sequence "+breakPoint.getSynapseSequenceType().toString()+" "+breakPoint.getKey());
                debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_FAILED);
                debugInterface.getPortListenWriter().flush();
            }else{
                //log.info("FAILED UN REGISTER BREAKPOINT - Non existing sequence "+breakPoint.getSynapseSequenceType().toString()+" "+breakPoint.getKey());
                debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_FAILED);
                debugInterface.getPortListenWriter().flush();

            }
        }

    }
    public void registerConnectorMediationFlowSkip(String connectorKey,String connectorMethod,int[] position,boolean registerMode){
        SynapseMediationFlowPoint skipPoint=new SynapseMediationFlowPoint();
        skipPoint.setSynapseMediationComponent(SynapseMediationComponent.CONNECTOR);
        skipPoint.setKey(connectorKey);
        skipPoint.setMediatorPosition(position);
        Mediator templateMediator=null;
        String template_key=SynapseDebugManagerConstants.CONNECTOR_PACKAGE+"."+connectorKey+"."+connectorMethod;
        templateMediator=synCfg.getSequenceTemplate(template_key);
        if(templateMediator!=null) {
            Mediator current_mediator = null;
            for (int counter = 0; counter < position.length; counter++) {
                if(counter==0){
                    current_mediator = ((AbstractListMediator) templateMediator).getChild(position[counter]);
                }
                if(current_mediator!=null) {
                    current_mediator = ((AbstractListMediator) current_mediator).getChild(position[counter]);
                }
            }
            if(current_mediator!=null) {
                skipPoint.setMediatorReference(current_mediator);
                if(registerMode) {
                    if (!((AbstractMediator) current_mediator).isSkipEnabled()) {
                        ((AbstractMediator) current_mediator).setSkipEnabled(true);
                        ((AbstractMediator) current_mediator).registerMediationFlowPoint(skipPoint);
                        // log.info("REGISTERED SKIPPOINT - " + skipPoint.getSynapseSequenceType().toString() + " " + skipPoint.getKey() + " " + toString(skipPoint.getMediatorPosition()));
                        debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_SUCCESS);
                        debugInterface.getPortListenWriter().flush();
                    } else {
                        // log.info("FAILED REGISTER SKIPPOINT - Already skip enabled " + skipPoint.getSynapseSequenceType().toString() + " " + skipPoint.getKey() + " " + toString(skipPoint.getMediatorPosition()));
                        debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_FAILED);
                        debugInterface.getPortListenWriter().flush();
                    }
                }else{
                    if(((AbstractMediator) current_mediator).isSkipEnabled()) {
                        ((AbstractMediator) current_mediator).setSkipEnabled(false);
                        ((AbstractMediator) current_mediator).unregisterMediationFlowPoint();
                        //  log.info("UN REGISTERED SKIPPOINT - " + skipPoint.getSynapseSequenceType().toString() + " " + skipPoint.getKey() + " " + toString(skipPoint.getMediatorPosition()));
                        debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_SUCCESS);
                        debugInterface.getPortListenWriter().flush();
                    }else{
                        //  log.info("FAILED REGISTER SKIPPOINT - Already skip disabled "+skipPoint.getSynapseSequenceType().toString() + " " + skipPoint.getKey() + " " + toString(skipPoint.getMediatorPosition()));
                        debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_FAILED);
                        debugInterface.getPortListenWriter().flush();
                    }

                }
            }else {
                if(registerMode) {
                    //log.info("FAILED REGISTER SKIPPOINT - Non existing mediator position "+skipPoint.getSynapseSequenceType().toString() + " " + skipPoint.getKey() + " " + toString(skipPoint.getMediatorPosition()));
                    debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_FAILED);
                    debugInterface.getPortListenWriter().flush();
                }else{
                    // log.info("FAILED UN REGISTER SKIPPOINT - Non existing mediator position "+skipPoint.getSynapseSequenceType().toString() + " " + skipPoint.getKey() + " " + toString(skipPoint.getMediatorPosition()));
                    debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_FAILED);
                    debugInterface.getPortListenWriter().flush();
                }
            }
        }else{
            if(registerMode) {
                //log.info("FAILED REGISTER SKIPPOINT - Non existing sequence "+skipPoint.getSynapseSequenceType().toString()+" "+skipPoint.getKey());
                debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_FAILED);
                debugInterface.getPortListenWriter().flush();
            }else {
                //log.info("FAILED UN REGISTER SKIPPOINT - Non existing sequence "+skipPoint.getSynapseSequenceType().toString()+" "+skipPoint.getKey()+" "+toString(skipPoint.getMediatorPosition()));
                debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_FAILED);
                debugInterface.getPortListenWriter().flush();
            }
        }

    }


    public void registerTemplateMediationFlowSkip(String templateKey,int[] position,boolean registerMode) {
        SynapseMediationFlowPoint skipPoint = new SynapseMediationFlowPoint();
        skipPoint.setSynapseMediationComponent(SynapseMediationComponent.TEMPLATE);
        skipPoint.setKey(templateKey);
        skipPoint.setMediatorPosition(position);
        Mediator templateMediator = null;
        templateMediator = synCfg.getSequenceTemplate(templateKey);
        if(templateMediator!=null) {
            Mediator current_mediator = null;
            for (int counter = 0; counter < position.length; counter++) {
                if(counter==0){
                    current_mediator = ((AbstractListMediator) templateMediator).getChild(position[counter]);
                }
                if(current_mediator!=null) {
                    current_mediator = ((AbstractListMediator) current_mediator).getChild(position[counter]);
                }
            }
            if(current_mediator!=null) {
                skipPoint.setMediatorReference(current_mediator);
                if(registerMode) {
                    if (!((AbstractMediator) current_mediator).isSkipEnabled()) {
                        ((AbstractMediator) current_mediator).setSkipEnabled(true);
                        ((AbstractMediator) current_mediator).registerMediationFlowPoint(skipPoint);
                        // log.info("REGISTERED SKIPPOINT - " + skipPoint.getSynapseSequenceType().toString() + " " + skipPoint.getKey() + " " + toString(skipPoint.getMediatorPosition()));
                        debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_SUCCESS);
                        debugInterface.getPortListenWriter().flush();
                    } else {
                        // log.info("FAILED REGISTER SKIPPOINT - Already skip enabled " + skipPoint.getSynapseSequenceType().toString() + " " + skipPoint.getKey() + " " + toString(skipPoint.getMediatorPosition()));
                        debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_FAILED);
                        debugInterface.getPortListenWriter().flush();
                    }
                }else{
                    if(((AbstractMediator) current_mediator).isSkipEnabled()) {
                        ((AbstractMediator) current_mediator).setSkipEnabled(false);
                        ((AbstractMediator) current_mediator).unregisterMediationFlowPoint();
                        //  log.info("UN REGISTERED SKIPPOINT - " + skipPoint.getSynapseSequenceType().toString() + " " + skipPoint.getKey() + " " + toString(skipPoint.getMediatorPosition()));
                        debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_SUCCESS);
                        debugInterface.getPortListenWriter().flush();
                    }else{
                        //  log.info("FAILED REGISTER SKIPPOINT - Already skip disabled "+skipPoint.getSynapseSequenceType().toString() + " " + skipPoint.getKey() + " " + toString(skipPoint.getMediatorPosition()));
                        debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_FAILED);
                        debugInterface.getPortListenWriter().flush();
                    }

                }
            }else {
                if(registerMode) {
                    //log.info("FAILED REGISTER SKIPPOINT - Non existing mediator position "+skipPoint.getSynapseSequenceType().toString() + " " + skipPoint.getKey() + " " + toString(skipPoint.getMediatorPosition()));
                    debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_FAILED);
                    debugInterface.getPortListenWriter().flush();
                }else{
                    // log.info("FAILED UN REGISTER SKIPPOINT - Non existing mediator position "+skipPoint.getSynapseSequenceType().toString() + " " + skipPoint.getKey() + " " + toString(skipPoint.getMediatorPosition()));
                    debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_FAILED);
                    debugInterface.getPortListenWriter().flush();
                }
            }
        }else{
            if(registerMode) {
                //log.info("FAILED REGISTER SKIPPOINT - Non existing sequence "+skipPoint.getSynapseSequenceType().toString()+" "+skipPoint.getKey());
                debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_FAILED);
                debugInterface.getPortListenWriter().flush();
            }else {
                //log.info("FAILED UN REGISTER SKIPPOINT - Non existing sequence "+skipPoint.getSynapseSequenceType().toString()+" "+skipPoint.getKey()+" "+toString(skipPoint.getMediatorPosition()));
                debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_FAILED);
                debugInterface.getPortListenWriter().flush();
            }
        }

    }



    public void registerTemplateMediationFlowBreakPoint(String templateKey,int[] position,boolean registerMode){
        SynapseMediationFlowPoint breakPoint=new SynapseMediationFlowPoint();
        breakPoint.setSynapseMediationComponent(SynapseMediationComponent.TEMPLATE);
        breakPoint.setKey(templateKey);
        breakPoint.setMediatorPosition(position);
        Mediator templateMediator=null;
        templateMediator=synCfg.getSequenceTemplate(templateKey);
        if(templateMediator!=null) {
            Mediator current_mediator = null;
            for (int counter = 0; counter < position.length; counter++) {
                if(counter==0){
                    current_mediator = ((AbstractListMediator) templateMediator).getChild(position[counter]);
                }
                if(current_mediator!=null) {
                    current_mediator = ((AbstractListMediator) current_mediator).getChild(position[counter]);
                }
            }
            if(current_mediator!=null) {
                breakPoint.setMediatorReference(current_mediator);
                if(registerMode) {
                    if (!((AbstractMediator) current_mediator).isBreakPoint()) {
                        ((AbstractMediator) current_mediator).setBreakPoint(true);
                        ((AbstractMediator) current_mediator).registerMediationFlowPoint(breakPoint);
                        //    log.info("REGISTERED BREAKPOINT - " + breakPoint.getSynapseSequenceType().toString() + " " + breakPoint.getKey() + " " + toString(breakPoint.getMediatorPosition()));
                        debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_SUCCESS);
                        debugInterface.getPortListenWriter().flush();
                    } else {
                        //      log.info("FAILED REGISTER SKIPPOINT - Already breakpoint enabled "+breakPoint.getSynapseSequenceType().toString() + " " + breakPoint.getKey() + " " + toString(breakPoint.getMediatorPosition()));
                        debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_FAILED);
                        debugInterface.getPortListenWriter().flush();
                    }
                }else{
                    if(((AbstractMediator)current_mediator).isBreakPoint()) {
                        ((AbstractMediator) current_mediator).setBreakPoint(false);
                        ((AbstractMediator) current_mediator).unregisterMediationFlowPoint();
                       // log.info("UN REGISTERED BREAKPOINT - " + breakPoint.getSynapseSequenceType().toString() + " " + breakPoint.getKey() + " " + toString(breakPoint.getMediatorPosition()));
                        debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_SUCCESS);
                        debugInterface.getPortListenWriter().flush();
                    }else{
                       // log.info("FAILED REGISTER SKIPPOINT - Already breakpoint disabled "+breakPoint.getSynapseSequenceType().toString() + " " + breakPoint.getKey() + " " + toString(breakPoint.getMediatorPosition()));
                        debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_FAILED);
                        debugInterface.getPortListenWriter().flush();
                    }

                }
            }else {
                if(registerMode) {
                    //   log.info("FAILED REGISTER BREAKPOINT - Non existing mediator position "+breakPoint.getSynapseSequenceType().toString() + " " + breakPoint.getKey() + " " + toString(breakPoint.getMediatorPosition()));
                    debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_FAILED);
                    debugInterface.getPortListenWriter().flush();
                }else{
                    // log.info("FAILED UN REGISTER BREAKPOINT - Non existing mediator position "+breakPoint.getSynapseSequenceType().toString() + " " + breakPoint.getKey() + " " + toString(breakPoint.getMediatorPosition()));
                    debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_FAILED);
                    debugInterface.getPortListenWriter().flush();

                }
            }
        }else{
            if(registerMode) {
                // log.info("FAILED REGISTER BREAKPOINT - Non existing sequence "+breakPoint.getSynapseSequenceType().toString()+" "+breakPoint.getKey());
                debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_FAILED);
                debugInterface.getPortListenWriter().flush();
            }else{
                //log.info("FAILED UN REGISTER BREAKPOINT - Non existing sequence "+breakPoint.getSynapseSequenceType().toString()+" "+breakPoint.getKey());
                debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_FAILED);
                debugInterface.getPortListenWriter().flush();

            }
        }

    }

   public void advertiseDebugEvent(String event){
        if(synEnv.isDebugEnabled()){
            debugInterface.getPortSendWriter().println(event);
            debugInterface.getPortSendWriter().flush();
        }
    }

    public void registerSequenceMediationFlowSkip(String seqType,String seqKey,int[] position,boolean registerMode){
        SynapseSequenceType synapseSequenceType = SynapseSequenceType.valueOf(seqType);
        SynapseMediationFlowPoint skipPoint=new SynapseMediationFlowPoint();
        skipPoint.setSynapseMediationComponent(SynapseMediationComponent.SEQUENCE);
        skipPoint.setKey(seqKey);
        skipPoint.setMediatorPosition(position);
        skipPoint.setSynapseSequenceType(synapseSequenceType);
        skipPoint.setSequenceMediationComponentIdentifier(SynapseDebugCommandConstants.DEBUG_COMMAND_MEDIATION_COMPONENT_SEQUENCE);
        Mediator seqMediator=null;
        if(synapseSequenceType.equals(SynapseSequenceType.NAMED)){
            seqMediator=synCfg.getSequence(seqKey);
        }
        if(seqMediator!=null) {
            Mediator current_mediator = null;
            for (int counter = 0; counter < position.length; counter++) {
                if(counter==0){
                    current_mediator = ((AbstractListMediator) seqMediator).getChild(position[counter]);
                }
                if(current_mediator!=null) {
                    current_mediator = ((AbstractListMediator) current_mediator).getChild(position[counter]);
                }
            }
            if(current_mediator!=null) {
                skipPoint.setMediatorReference(current_mediator);
                if(registerMode) {
                    if (!((AbstractMediator) current_mediator).isSkipEnabled()) {
                        ((AbstractMediator) current_mediator).setSkipEnabled(true);
                        ((AbstractMediator) current_mediator).registerMediationFlowPoint(skipPoint);
                       // log.info("REGISTERED SKIPPOINT - " + skipPoint.getSynapseSequenceType().toString() + " " + skipPoint.getKey() + " " + toString(skipPoint.getMediatorPosition()));
                        debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_SUCCESS);
                        debugInterface.getPortListenWriter().flush();
                    } else {
                       // log.info("FAILED REGISTER SKIPPOINT - Already skip enabled " + skipPoint.getSynapseSequenceType().toString() + " " + skipPoint.getKey() + " " + toString(skipPoint.getMediatorPosition()));
                        debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_FAILED);
                        debugInterface.getPortListenWriter().flush();
                    }
                }else{
                    if(((AbstractMediator) current_mediator).isSkipEnabled()) {
                        ((AbstractMediator) current_mediator).setSkipEnabled(false);
                        ((AbstractMediator) current_mediator).unregisterMediationFlowPoint();
                      //  log.info("UN REGISTERED SKIPPOINT - " + skipPoint.getSynapseSequenceType().toString() + " " + skipPoint.getKey() + " " + toString(skipPoint.getMediatorPosition()));
                        debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_SUCCESS);
                        debugInterface.getPortListenWriter().flush();
                    }else{
                      //  log.info("FAILED REGISTER SKIPPOINT - Already skip disabled "+skipPoint.getSynapseSequenceType().toString() + " " + skipPoint.getKey() + " " + toString(skipPoint.getMediatorPosition()));
                        debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_FAILED);
                        debugInterface.getPortListenWriter().flush();
                    }

                }
            }else {
                if(registerMode) {
                    //log.info("FAILED REGISTER SKIPPOINT - Non existing mediator position "+skipPoint.getSynapseSequenceType().toString() + " " + skipPoint.getKey() + " " + toString(skipPoint.getMediatorPosition()));
                    debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_FAILED);
                    debugInterface.getPortListenWriter().flush();
                }else{
                   // log.info("FAILED UN REGISTER SKIPPOINT - Non existing mediator position "+skipPoint.getSynapseSequenceType().toString() + " " + skipPoint.getKey() + " " + toString(skipPoint.getMediatorPosition()));
                    debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_FAILED);
                    debugInterface.getPortListenWriter().flush();
                }
            }
        }else{
            if(registerMode) {
                //log.info("FAILED REGISTER SKIPPOINT - Non existing sequence "+skipPoint.getSynapseSequenceType().toString()+" "+skipPoint.getKey());
                debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_FAILED);
                debugInterface.getPortListenWriter().flush();
            }else {
                //log.info("FAILED UN REGISTER SKIPPOINT - Non existing sequence "+skipPoint.getSynapseSequenceType().toString()+" "+skipPoint.getKey()+" "+toString(skipPoint.getMediatorPosition()));
                debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_FAILED);
                debugInterface.getPortListenWriter().flush();
            }
        }
    }

    public void registerSequenceMediationFlowBreakPoint(String sequenceType,String seqKey,int[] position,boolean registerMode){
        SynapseSequenceType synapseSequenceType = SynapseSequenceType.valueOf(sequenceType);
        SynapseMediationFlowPoint breakPoint=new SynapseMediationFlowPoint();
        breakPoint.setSynapseMediationComponent(SynapseMediationComponent.SEQUENCE);
        breakPoint.setKey(seqKey);
        breakPoint.setMediatorPosition(position);
        breakPoint.setSynapseSequenceType(synapseSequenceType);
        breakPoint.setSequenceMediationComponentIdentifier(SynapseDebugCommandConstants.DEBUG_COMMAND_MEDIATION_COMPONENT_SEQUENCE);
        Mediator seqMediator=null;
        if(synapseSequenceType.equals(SynapseSequenceType.NAMED)){
            seqMediator=synCfg.getSequence(seqKey);
        }
        if(seqMediator!=null) {
            Mediator current_mediator = null;
            for (int counter = 0; counter < position.length; counter++) {
                if(counter==0){
                    current_mediator = ((AbstractListMediator) seqMediator).getChild(position[counter]);
                }
                if(current_mediator!=null) {
                    current_mediator = ((AbstractListMediator) current_mediator).getChild(position[counter]);
                }
            }
            if(current_mediator!=null) {
                breakPoint.setMediatorReference(current_mediator);
                if(registerMode) {
                    if (!((AbstractMediator) current_mediator).isBreakPoint()) {
                        ((AbstractMediator) current_mediator).setBreakPoint(true);
                        ((AbstractMediator) current_mediator).registerMediationFlowPoint(breakPoint);
                        //    log.info("REGISTERED BREAKPOINT - " + breakPoint.getSynapseSequenceType().toString() + " " + breakPoint.getKey() + " " + toString(breakPoint.getMediatorPosition()));
                        debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_SUCCESS);
                        debugInterface.getPortListenWriter().flush();
                    } else {
                        //      log.info("FAILED REGISTER SKIPPOINT - Already breakpoint enabled "+breakPoint.getSynapseSequenceType().toString() + " " + breakPoint.getKey() + " " + toString(breakPoint.getMediatorPosition()));
                        debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_FAILED);
                        debugInterface.getPortListenWriter().flush();
                    }
                }else{
                    if(((AbstractMediator)current_mediator).isBreakPoint()) {
                        ((AbstractMediator) current_mediator).setBreakPoint(false);
                        ((AbstractMediator) current_mediator).unregisterMediationFlowPoint();
                   //     log.info("UN REGISTERED BREAKPOINT - " + breakPoint.getSynapseSequenceType().toString() + " " + breakPoint.getKey() + " " + toString(breakPoint.getMediatorPosition()));
                        debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_SUCCESS);
                        debugInterface.getPortListenWriter().flush();
                    }else{
                   //     log.info("FAILED REGISTER SKIPPOINT - Already breakpoint disabled "+breakPoint.getSynapseSequenceType().toString() + " " + breakPoint.getKey() + " " + toString(breakPoint.getMediatorPosition()));
                        debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_FAILED);
                        debugInterface.getPortListenWriter().flush();
                    }

                }
            }else {
                if(registerMode) {
                    //   log.info("FAILED REGISTER BREAKPOINT - Non existing mediator position "+breakPoint.getSynapseSequenceType().toString() + " " + breakPoint.getKey() + " " + toString(breakPoint.getMediatorPosition()));
                    debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_FAILED);
                    debugInterface.getPortListenWriter().flush();
                }else{
                  //  log.info("FAILED UN REGISTER BREAKPOINT - Non existing mediator position "+breakPoint.getSynapseSequenceType().toString() + " " + breakPoint.getKey() + " " + toString(breakPoint.getMediatorPosition()));
                    debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_FAILED);
                    debugInterface.getPortListenWriter().flush();

                }
            }
        }else{
            if(registerMode) {
                // log.info("FAILED REGISTER BREAKPOINT - Non existing sequence "+breakPoint.getSynapseSequenceType().toString()+" "+breakPoint.getKey());
                debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_FAILED);
                debugInterface.getPortListenWriter().flush();
            }else{
               // log.info("FAILED UN REGISTER BREAKPOINT - Non existing sequence "+breakPoint.getSynapseSequenceType().toString()+" "+breakPoint.getKey());
                debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_FAILED);
                debugInterface.getPortListenWriter().flush();

            }
        }
    }


    public void debugResume(){
        medFlowState=MediationFlowState.RESUMED;
        debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_SUCCESS);
        debugInterface.getPortListenWriter().flush();
    }



    public JSONObject createDebugCommandResponse(){

    }

    public JSONObject createDebugEvent(){

    }


/*
   public void unregisterMediationFlowBreakPoint(String breakPointType,String seqKey,int[] position){
        SynapseSequenceType synapseSequenceType = SynapseSequenceType.valueOf(breakPointType);
        SynapseMediationFlowPoint breakPoint=new SynapseMediationFlowPoint();
        breakPoint.setKey(seqKey);
        breakPoint.setMediatorPosition(position);
        breakPoint.setSynapseSequenceType(synapseSequenceType);
        Mediator seqMediator=null;
        if(synapseSequenceType.equals(SynapseSequenceType.NAMED)){
            seqMediator=synCfg.getSequence(seqKey);
        }else if(synapseSequenceType.equals(SynapseSequenceType.PROXY_INSEQ)){
            seqMediator=synCfg.getProxyService(seqKey).getTargetInLineInSequence();
        }else if(synapseSequenceType.equals(SynapseSequenceType.PROXY_OUTSEQ)){
            seqMediator=synCfg.getProxyService(seqKey).getTargetInLineOutSequence();
        }else if(synapseSequenceType.equals(SynapseSequenceType.PROXY_FAULTSEQ)){
            seqMediator=synCfg.getProxyService(seqKey).getTargetInLineFaultSequence();
        }else if(synapseSequenceType.equals(SynapseSequenceType.API_INSEQ)){
            seqMediator=synCfg.getAPI(seqKey).getResource((String) synCtx.getProperty(RESTConstants.SYNAPSE_RESOURCE)).getInSequence();
        }else if(synapseSequenceType.equals(SynapseSequenceType.API_OUTSEQ)){
            seqMediator=synCfg.getAPI(seqKey).getResource((String) synCtx.getProperty(RESTConstants.SYNAPSE_RESOURCE)).getOutSequence();
        }else if(synapseSequenceType.equals(SynapseSequenceType.API_FAULTSEQ)){
            seqMediator=synCfg.getAPI(seqKey).getResource((String) synCtx.getProperty(RESTConstants.SYNAPSE_RESOURCE)).getFaultSequence();
        }
        if(seqMediator!=null) {
            Mediator current_mediator = ((AbstractListMediator) seqMediator).getChild(position[0]);
            for (int counter = 1; counter < position.length; counter++) {
                if(current_mediator!=null) {
                    current_mediator = ((AbstractListMediator) current_mediator).getChild(position[counter]);
                }
            }
            if(current_mediator!=null) {
                breakPoint.setMediatorReference(current_mediator);
                if(((AbstractMediator)current_mediator).isBreakPoint()) {
                    ((AbstractMediator) current_mediator).setBreakPoint(false);
                    ((AbstractMediator) current_mediator).unregisterMediationFlowPoint();
                    log.info("UN REGISTERED BREAKPOINT - " + breakPoint.getSynapseSequenceType().toString() + " " + breakPoint.getKey() + " " + toString(breakPoint.getMediatorPosition()));
                    debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_SUCCESS);
                    debugInterface.getPortListenWriter().flush();
                }else{
                    log.info("FAILED REGISTER SKIPPOINT - Already breakpoint disabled "+breakPoint.getSynapseSequenceType().toString() + " " + breakPoint.getKey() + " " + toString(breakPoint.getMediatorPosition()));
                    debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_FAILED);
                    debugInterface.getPortListenWriter().flush();
                }
            }else{
                log.info("FAILED UN REGISTER BREAKPOINT - Non existing mediator position "+breakPoint.getSynapseSequenceType().toString() + " " + breakPoint.getKey() + " " + toString(breakPoint.getMediatorPosition()));
                debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_FAILED);
                debugInterface.getPortListenWriter().flush();
            }
        }else{
            log.info("FAILED UN REGISTER BREAKPOINT - Non existing sequence "+breakPoint.getSynapseSequenceType().toString()+" "+breakPoint.getKey());
            debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_FAILED);
            debugInterface.getPortListenWriter().flush();

        }
    }

    public void unregisterMediationFlowSkipPoint(String seqType,String seqKey,int[] position){
        SynapseSequenceType synapseSequenceType = SynapseSequenceType.valueOf(seqType);
        SynapseMediationFlowPoint skipPoint=new SynapseMediationFlowPoint();
        skipPoint.setKey(seqKey);
        skipPoint.setMediatorPosition(position);
        skipPoint.setSynapseSequenceType(synapseSequenceType);
        Mediator seqMediator=null;
        if(synapseSequenceType.equals(SynapseSequenceType.NAMED)){
            seqMediator=synCfg.getSequence(seqKey);
        }else if(synapseSequenceType.equals(SynapseSequenceType.PROXY_INSEQ)){
            seqMediator=synCfg.getProxyService(seqKey).getTargetInLineInSequence();
        }else if(synapseSequenceType.equals(SynapseSequenceType.PROXY_OUTSEQ)){
            seqMediator=synCfg.getProxyService(seqKey).getTargetInLineOutSequence();
        }else if(synapseSequenceType.equals(SynapseSequenceType.PROXY_FAULTSEQ)){
            seqMediator=synCfg.getProxyService(seqKey).getTargetInLineFaultSequence();
        }else if(synapseSequenceType.equals(SynapseSequenceType.API_INSEQ)){
            seqMediator=synCfg.getAPI(seqKey).getResource((String) synCtx.getProperty(RESTConstants.SYNAPSE_RESOURCE)).getInSequence();
        }else if(synapseSequenceType.equals(SynapseSequenceType.API_OUTSEQ)){
            seqMediator=synCfg.getAPI(seqKey).getResource((String) synCtx.getProperty(RESTConstants.SYNAPSE_RESOURCE)).getOutSequence();
        }else if(synapseSequenceType.equals(SynapseSequenceType.API_FAULTSEQ)){
            seqMediator=synCfg.getAPI(seqKey).getResource((String) synCtx.getProperty(RESTConstants.SYNAPSE_RESOURCE)).getFaultSequence();
        }
        if(seqMediator!=null) {
            Mediator current_mediator = ((AbstractListMediator) seqMediator).getChild(position[0]);
            for (int counter = 1; counter < position.length; counter++) {
                if(current_mediator!=null) {
                    current_mediator = ((AbstractListMediator) current_mediator).getChild(position[counter]);
                }
            }
            if(current_mediator!=null) {
                skipPoint.setMediatorReference(current_mediator);
                if(((AbstractMediator) current_mediator).isSkipEnabled()) {
                    ((AbstractMediator) current_mediator).setSkipEnabled(false);
                    ((AbstractMediator) current_mediator).unregisterMediationFlowPoint();
                    log.info("UN REGISTERED SKIPPOINT - " + skipPoint.getSynapseSequenceType().toString() + " " + skipPoint.getKey() + " " + toString(skipPoint.getMediatorPosition()));
                    debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_SUCCESS);
                    debugInterface.getPortListenWriter().flush();
                }else{
                    log.info("FAILED REGISTER SKIPPOINT - Already skip disabled "+skipPoint.getSynapseSequenceType().toString() + " " + skipPoint.getKey() + " " + toString(skipPoint.getMediatorPosition()));
                    debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_FAILED);
                    debugInterface.getPortListenWriter().flush();
                }
            }else{
                log.info("FAILED UN REGISTER SKIPPOINT - Non existing mediator position "+skipPoint.getSynapseSequenceType().toString() + " " + skipPoint.getKey() + " " + toString(skipPoint.getMediatorPosition()));
                debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_FAILED);
                debugInterface.getPortListenWriter().flush();
            }
        }else{
            log.info("FAILED UN REGISTER SKIPPOINT - Non existing sequence "+skipPoint.getSynapseSequenceType().toString()+" "+skipPoint.getKey()+" "+toString(skipPoint.getMediatorPosition()));
            debugInterface.getPortListenWriter().println(SynapseDebugCommandConstants.DEBUG_COMMAND_FAILED);
            debugInterface.getPortListenWriter().flush();
        }
    }

   */

   public String toString(int[] position){
        String positionString="";
        for(int counter=0;counter<position.length;counter++){
            positionString=positionString.concat(String.valueOf(position[counter])).concat(" ");
        }
        return  positionString;
   }

   public void acquireMediationFlowPointProperties(String propertyOrProperties,String propertyContext, JSONObject property_arguments){
       try {
        if(propertyOrProperties.equals(SynapseDebugCommandConstants.DEBUG_COMMAND_PROPERTIES)) {
            if(propertyContext.equals(SynapseDebugCommandConstants.DEBUG_COMMAND_PROPERTY_CONTEXT_ALL)) {
                JSONObject data_axis2 = new JSONObject(((Axis2MessageContext) synCtx).getAxis2MessageContext().getProperties());
                JSONObject data_synapse = new JSONObject(((Axis2MessageContext) synCtx).getProperties());
                JSONObject data_axis2_prop = new JSONObject();
                JSONObject data_synapse_prop = new JSONObject();
                data_axis2_prop.put("axis2-properties", data_axis2);
                data_synapse_prop.put("synapse-properties", data_synapse);
                JSONArray data_array = new JSONArray();
                data_array.put(data_axis2_prop);
                data_array.put(data_synapse_prop);
                debugInterface.getPortListenWriter().println(data_array.toString(3));
                debugInterface.getPortListenWriter().flush();
            }else if (propertyContext.equals(SynapseDebugCommandConstants.DEBUG_COMMAND_PROPERTY_CONTEXT_AXIS2)){
                JSONObject data_axis2 = new JSONObject(((Axis2MessageContext) synCtx).getAxis2MessageContext().getProperties());
                JSONObject data_axis2_prop = new JSONObject();
                data_axis2_prop.put("axis2-properties", data_axis2);
                debugInterface.getPortListenWriter().println(data_axis2_prop.toString(3));
                debugInterface.getPortListenWriter().flush();

            }else if (propertyContext.equals(SynapseDebugCommandConstants.DEBUG_COMMAND_PROPERTY_CONTEXT_SYNAPSE)||propertyContext.equals(SynapseDebugCommandConstants.DEBUG_COMMAND_PROPERTY_CONTEXT_DEFAULT)){
                JSONObject data_synapse = new JSONObject(((Axis2MessageContext) synCtx).getProperties());
                JSONObject data_synapse_prop = new JSONObject();
                data_synapse_prop.put("synapse-properties", data_synapse);
                debugInterface.getPortListenWriter().println(data_synapse_prop.toString(3));
                debugInterface.getPortListenWriter().flush();

            }else if(propertyContext.equals(SynapseDebugCommandConstants.DEBUG_COMMAND_PROPERTY_CONTEXT_AXIS2CLIENT)){
                JSONObject data_axis2 = new JSONObject(((Axis2MessageContext) synCtx).getAxis2MessageContext().getOptions().getProperties());
                JSONObject data_axis2_prop = new JSONObject();
                data_axis2_prop.put("axis2Client-properties", data_axis2);
                debugInterface.getPortListenWriter().println(data_axis2_prop.toString(3));
                debugInterface.getPortListenWriter().flush();
            }else if(propertyContext.equals(SynapseDebugCommandConstants.DEBUG_COMMAND_PROPERTY_CONTEXT_TRANSPORT)){
                JSONObject data_axis2 = new JSONObject((Map)((Axis2MessageContext) synCtx).getAxis2MessageContext().getProperty(
                        org.apache.axis2.context.MessageContext.TRANSPORT_HEADERS));
                JSONObject data_axis2_prop = new JSONObject();
                data_axis2_prop.put("axis2Transport-properties", data_axis2);
                debugInterface.getPortListenWriter().println(data_axis2_prop.toString(3));
                debugInterface.getPortListenWriter().flush();
            }else if(propertyContext.equals(SynapseDebugCommandConstants.DEBUG_COMMAND_PROPERTY_CONTEXT_OPERATION)){
                JSONObject data_axis2 = new JSONObject(((Axis2MessageContext) synCtx).getAxis2MessageContext().getOperationContext().getProperties());
                JSONObject data_axis2_prop = new JSONObject();
                data_axis2_prop.put("axis2Client-properties", data_axis2);
                debugInterface.getPortListenWriter().println(data_axis2_prop.toString(3));
                debugInterface.getPortListenWriter().flush();
            }
        }else if(propertyOrProperties.equals(SynapseDebugCommandConstants.DEBUG_COMMAND_PROPERTY)){
           if (propertyContext.equals(SynapseDebugCommandConstants.DEBUG_COMMAND_PROPERTY_CONTEXT_AXIS2)){
               JSONObject data_axis2 = new JSONObject(((Axis2MessageContext) synCtx).getAxis2MessageContext().getProperties());
               Object result=null;
               if(data_axis2.has(property_arguments.getString(SynapseDebugCommandConstants.DEBUG_COMMAND_PROPERTY_NAME))){
                    result=data_axis2.get(property_arguments.getString(SynapseDebugCommandConstants.DEBUG_COMMAND_PROPERTY_NAME));
               }
               JSONObject json_result=new JSONObject();
               json_result.put(property_arguments.getString(SynapseDebugCommandConstants.DEBUG_COMMAND_PROPERTY_NAME),result);
               debugInterface.getPortListenWriter().println(json_result.toString(3));
               debugInterface.getPortListenWriter().flush();

            }else if (propertyContext.equals(SynapseDebugCommandConstants.DEBUG_COMMAND_PROPERTY_CONTEXT_SYNAPSE)||propertyContext.equals(SynapseDebugCommandConstants.DEBUG_COMMAND_PROPERTY_CONTEXT_DEFAULT)){
               JSONObject data_synapse = new JSONObject(((Axis2MessageContext) synCtx).getProperties());
               Object result=null;
               if(data_synapse.has(property_arguments.getString(SynapseDebugCommandConstants.DEBUG_COMMAND_PROPERTY_NAME))){
                   result=data_synapse.getJSONObject(property_arguments.getString(SynapseDebugCommandConstants.DEBUG_COMMAND_PROPERTY_NAME));
               }
               JSONObject json_result=new JSONObject();
               json_result.put(property_arguments.getString(SynapseDebugCommandConstants.DEBUG_COMMAND_PROPERTY_NAME),result);
               debugInterface.getPortListenWriter().println(json_result.toString(3));
               debugInterface.getPortListenWriter().flush();

            }else if (propertyContext.equals(SynapseDebugCommandConstants.DEBUG_COMMAND_PROPERTY_CONTEXT_AXIS2CLIENT)){
               JSONObject data_axis2 = new JSONObject(((Axis2MessageContext) synCtx).getAxis2MessageContext().getOptions().getProperties());
               Object result=null;
               if(data_axis2.has(property_arguments.getString(SynapseDebugCommandConstants.DEBUG_COMMAND_PROPERTY_NAME))){
                   result=data_axis2.get(property_arguments.getString(SynapseDebugCommandConstants.DEBUG_COMMAND_PROPERTY_NAME));
               }
               JSONObject json_result=new JSONObject();
               json_result.put(property_arguments.getString(SynapseDebugCommandConstants.DEBUG_COMMAND_PROPERTY_NAME),result);
               debugInterface.getPortListenWriter().println(json_result.toString(3));
               debugInterface.getPortListenWriter().flush();

           }else if (propertyContext.equals(SynapseDebugCommandConstants.DEBUG_COMMAND_PROPERTY_CONTEXT_TRANSPORT)){
               JSONObject data_axis2 = new JSONObject((Map)((Axis2MessageContext) synCtx).getAxis2MessageContext().getProperty(
                       org.apache.axis2.context.MessageContext.TRANSPORT_HEADERS));
               Object result=null;
               if(data_axis2.has(property_arguments.getString(SynapseDebugCommandConstants.DEBUG_COMMAND_PROPERTY_NAME))){
                   result=data_axis2.get(property_arguments.getString(SynapseDebugCommandConstants.DEBUG_COMMAND_PROPERTY_NAME));
               }
               JSONObject json_result=new JSONObject();
               json_result.put(property_arguments.getString(SynapseDebugCommandConstants.DEBUG_COMMAND_PROPERTY_NAME),result);
               debugInterface.getPortListenWriter().println(json_result.toString(3));
               debugInterface.getPortListenWriter().flush();

           }else if (propertyContext.equals(SynapseDebugCommandConstants.DEBUG_COMMAND_PROPERTY_CONTEXT_OPERATION)){
               JSONObject data_axis2 = new JSONObject(((Axis2MessageContext) synCtx).getAxis2MessageContext().getOperationContext().getProperties());
               Object result=null;
               if(data_axis2.has(property_arguments.getString(SynapseDebugCommandConstants.DEBUG_COMMAND_PROPERTY_NAME))){
                   result=data_axis2.get(property_arguments.getString(SynapseDebugCommandConstants.DEBUG_COMMAND_PROPERTY_NAME));
               }
               JSONObject json_result=new JSONObject();
               json_result.put(property_arguments.getString(SynapseDebugCommandConstants.DEBUG_COMMAND_PROPERTY_NAME),result);
               debugInterface.getPortListenWriter().println(json_result.toString(3));
               debugInterface.getPortListenWriter().flush();
           }
        }
        }catch(JSONException ex){
               log.error("Failed to set scope properties");
        }
    }

    public void addMediationFlowPointProperty(String propertyContext, JSONObject property_arguments,boolean isActionSet){
        try {
            String property_key=property_arguments.getString(SynapseDebugCommandConstants.DEBUG_COMMAND_PROPERTY_NAME);
            String property_value=property_arguments.getString(SynapseDebugCommandConstants.DEBUG_COMMAND_PROPERTY_VALUE);
            if (isActionSet) {

                if (propertyContext.equals(SynapseDebugCommandConstants.DEBUG_COMMAND_PROPERTY_CONTEXT_DEFAULT) || propertyContext.equals(SynapseDebugCommandConstants.DEBUG_COMMAND_PROPERTY_CONTEXT_SYNAPSE)) {
                    //Setting property into the  Synapse Context
                    synCtx.setProperty(property_key, property_value);

                } else if (propertyContext.equals(SynapseDebugCommandConstants.DEBUG_COMMAND_PROPERTY_CONTEXT_AXIS2)
                        && synCtx instanceof Axis2MessageContext) {
                    //Setting property into the  Axis2 Message Context
                    Axis2MessageContext axis2smc = (Axis2MessageContext) synCtx;
                    org.apache.axis2.context.MessageContext axis2MessageCtx =
                            axis2smc.getAxis2MessageContext();
                    axis2MessageCtx.setProperty(property_key, property_value);
                    if (org.apache.axis2.Constants.Configuration.MESSAGE_TYPE.equals(property_key)) {
                        axis2MessageCtx.setProperty(org.apache.axis2.Constants.Configuration.CONTENT_TYPE, property_value);
                        Object o = axis2MessageCtx.getProperty(org.apache.axis2.context.MessageContext.TRANSPORT_HEADERS);
                        Map _headers = (Map) o;
                        if (_headers != null) {
                            _headers.remove(HTTP.CONTENT_TYPE);
                            _headers.put(HTTP.CONTENT_TYPE, property_value);
                        }
                    }

                } else if (propertyContext.equals(SynapseDebugCommandConstants.DEBUG_COMMAND_PROPERTY_CONTEXT_AXIS2CLIENT)
                        && synCtx instanceof Axis2MessageContext) {
                    //Setting property into the  Axis2 Message Context client options
                    Axis2MessageContext axis2smc = (Axis2MessageContext) synCtx;
                    org.apache.axis2.context.MessageContext axis2MessageCtx =
                            axis2smc.getAxis2MessageContext();
                    axis2MessageCtx.getOptions().setProperty(property_key, property_value);

                } else if (propertyContext.equals(SynapseDebugCommandConstants.DEBUG_COMMAND_PROPERTY_CONTEXT_TRANSPORT)
                        && synCtx instanceof Axis2MessageContext) {
                    //Setting Transport Headers
                    Axis2MessageContext axis2smc = (Axis2MessageContext) synCtx;
                    org.apache.axis2.context.MessageContext axis2MessageCtx =
                            axis2smc.getAxis2MessageContext();
                    Object headers = axis2MessageCtx.getProperty(
                            org.apache.axis2.context.MessageContext.TRANSPORT_HEADERS);

                    if (headers != null && headers instanceof Map) {
                        Map headersMap = (Map) headers;
                        headersMap.put(property_key, property_value);
                    }
                    if (headers == null) {
                        Map headersMap = new HashMap();
                        headersMap.put(property_key, property_value);
                        axis2MessageCtx.setProperty(
                                org.apache.axis2.context.MessageContext.TRANSPORT_HEADERS,
                                headersMap);
                    }
                }else if(propertyContext.equals(SynapseDebugCommandConstants.DEBUG_COMMAND_PROPERTY_CONTEXT_OPERATION)
                        && synCtx instanceof Axis2MessageContext){
                    //Setting Transport Headers
                    Axis2MessageContext axis2smc = (Axis2MessageContext) synCtx;
                    org.apache.axis2.context.MessageContext axis2MessageCtx =
                            axis2smc.getAxis2MessageContext();
                    axis2smc.getAxis2MessageContext().getOperationContext().setProperty(property_key, property_value);
                }

            } else {
                if (propertyContext == null || XMLConfigConstants.SCOPE_DEFAULT.equals(propertyContext)) {
                    //Removing property from the  Synapse Context
                    Set pros = synCtx.getPropertyKeySet();
                    if (pros != null) {
                        pros.remove(property_key);
                    }

                } else if ((propertyContext.equals(SynapseDebugCommandConstants.DEBUG_COMMAND_PROPERTY_CONTEXT_AXIS2) ||
                        propertyContext.equals(SynapseDebugCommandConstants.DEBUG_COMMAND_PROPERTY_CONTEXT_AXIS2CLIENT))
                        && synCtx instanceof Axis2MessageContext) {

                    //Removing property from the Axis2 Message Context
                    Axis2MessageContext axis2smc = (Axis2MessageContext) synCtx;
                    org.apache.axis2.context.MessageContext axis2MessageCtx =
                            axis2smc.getAxis2MessageContext();
                    axis2MessageCtx.removeProperty(property_key);

                } else if (propertyContext.equals(SynapseDebugCommandConstants.DEBUG_COMMAND_PROPERTY_CONTEXT_TRANSPORT)
                        && synCtx instanceof Axis2MessageContext) {
                    // Removing transport headers
                    Axis2MessageContext axis2smc = (Axis2MessageContext) synCtx;
                    org.apache.axis2.context.MessageContext axis2MessageCtx =
                            axis2smc.getAxis2MessageContext();
                    Object headers = axis2MessageCtx.getProperty(
                            org.apache.axis2.context.MessageContext.TRANSPORT_HEADERS);
                    if (headers != null && headers instanceof Map) {
                        Map headersMap = (Map) headers;
                        headersMap.remove(property_key);
                    } else {
                       // synLog.traceOrDebug("No transport headers found for the message");
                    }
                }
            }


        } catch (JSONException e) {
            log.error("Failed to set scope properties");
        }


    }



}
