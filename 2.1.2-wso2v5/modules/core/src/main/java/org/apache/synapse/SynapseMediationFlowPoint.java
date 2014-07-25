/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *   * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */


package org.apache.synapse;


public class SynapseMediationFlowPoint {
    private SynapseMediationComponent medComponent=null;
    private SynapseSequenceType seqType=null;
    private Mediator medRef=null;
    private String key=null;
    private int[] mediatorPosition=null;
    private String api_identifier_mapping=null;
    private String api_identifier_method=null;
    private String sequence_mediation_component_identifier=null;
    public String getAPIIdentifierMapping(){return api_identifier_mapping;}
    public void setAPIIdentifierMapping(String api_identifier_mapping){this.api_identifier_mapping=api_identifier_mapping;}
    public String getAPIIdentifierMethod(){return api_identifier_mapping;}
    public void setAPIIdentifierMethod(String api_identifier_method){this.api_identifier_method=api_identifier_method;}
    public String getSequenceMediationComponentIdentifier(){return sequence_mediation_component_identifier;}
    public void setSequenceMediationComponentIdentifier(String sequence_mediation_component_identifier){this.sequence_mediation_component_identifier=sequence_mediation_component_identifier;}
    public SynapseMediationComponent getSynapseMediationComponent(){return medComponent;}
    public void setSynapseMediationComponent(SynapseMediationComponent medComponent){this.medComponent=medComponent;}
    public String getKey(){return key;}
    public void setKey(String key){this.key=key;}
    public void setMediatorPosition(int[] mediatorPosition){this.mediatorPosition=mediatorPosition;}
    public int[] getMediatorPosition(){return mediatorPosition;}
    public void setSynapseSequenceType(SynapseSequenceType seqType){this.seqType=seqType;}
    public SynapseSequenceType getSynapseSequenceType(){return seqType;}
    public void setMediatorReference(Mediator reference){this.medRef=medRef;}
    public Mediator getMediatorReference(Mediator reference){return medRef;}
    public String toString(){
        return "";
    }

}
