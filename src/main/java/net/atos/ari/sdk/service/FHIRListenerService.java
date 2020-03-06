/*
 * Copyright (C) 2020  Atos Spain SA. All rights reserved.
 * 
 * This file is part of the xcare-listener.
 * 
 * FHIRListenerService.java is free software: you can redistribute it and/or modify it under the 
 * terms of the Apache License, Version 2.0 (the License);
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * The software is provided "AS IS", without any warranty of any kind, express or implied,
 * including but not limited to the warranties of merchantability, fitness for a particular
 * purpose and noninfringement, in no event shall the authors or copyright holders be 
 * liable for any claim, damages or other liability, whether in action of contract, tort or
 * otherwise, arising from, out of or in connection with the software or the use or other
 * dealings in the software.
 * 
 * See README file for the full disclaimer information and LICENSE file for full license 
 * information in the project root.
 * 
 * @author Atos Research and Innovation, Atos SPAIN SA
 * 
 * FHIR Listener to interact with the instance
 */

package net.atos.ari.sdk.service;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.model.Organization;
import org.hl7.fhir.dstu3.model.Attachment;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.dstu3.model.Bundle.BundleType;
import org.hl7.fhir.dstu3.model.Bundle.HTTPVerb;
import org.hl7.fhir.dstu3.model.CodeableConcept;
import org.hl7.fhir.dstu3.model.Coding;
import org.hl7.fhir.dstu3.model.DateTimeType;
import org.hl7.fhir.dstu3.model.DocumentReference;
import org.hl7.fhir.dstu3.model.Encounter;
import org.hl7.fhir.dstu3.model.Encounter.EncounterStatus;
import org.hl7.fhir.dstu3.model.DocumentReference.DocumentReferenceContentComponent;
import org.hl7.fhir.dstu3.model.DocumentReference.DocumentReferenceContextComponent;
import org.hl7.fhir.dstu3.model.DocumentReference.ReferredDocumentStatus;
import org.hl7.fhir.dstu3.model.Enumerations.DocumentReferenceStatus;
import org.hl7.fhir.dstu3.model.EpisodeOfCare;
import org.hl7.fhir.dstu3.model.Identifier;
import org.hl7.fhir.dstu3.model.Observation;
import org.hl7.fhir.dstu3.model.Observation.ObservationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.hl7.fhir.dstu3.model.Patient;
import org.hl7.fhir.dstu3.model.Period;
import org.hl7.fhir.dstu3.model.Practitioner;
import org.hl7.fhir.dstu3.model.Quantity;
import org.hl7.fhir.dstu3.model.Reference;
import org.hl7.fhir.dstu3.model.Type;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.primitive.IdDt;
import ca.uhn.fhir.rest.client.api.IGenericClient;

@Component
public class FHIRListenerService implements ListenerService {

    private static final Logger logger = LoggerFactory.getLogger(FHIRListenerService.class);

    private final String CATEGORY_CODE = "activity";
    private final String CLINIC = "http://clinic.org";

    private final String PREHAB_CODE = "102PH";
    private final String PREHAB_DISPLAY = "VISITA PRE-HABILITACIO";

    /** FHIR base url path. */
    @Value("${fhir.base.url}")
    private String fhirUrl;

    /** REDCap language. */
    @Value("${redcap.language}")
    private String language;

    private final FhirContext ctx = FhirContext.forDstu3();

    private IGenericClient client = null;

    public void configure() {
        client = ctx.newRestfulGenericClient(fhirUrl);
    }

    public Map<String, Object> export() {
        // Get all the patients in the system
        Bundle results = client.search()
            .forResource(Patient.class)
            .returnBundle(Bundle.class)
            .execute();

        Map<String, Object> response = new HashMap<String, Object>();
        // Obtain the patient by NHC
        for (BundleEntryComponent entry : results.getEntry()) {
            Patient patient = (Patient) entry.getResource();
            response.put(patient.getId(), patient);
        }
        return response;

    }

    public void execute(Bundle bundle) {

        Bundle resp = client.transaction()
            .withBundle(bundle)
            .execute();
    }

    /**
     * Get an HL7 FHIR Patient object given the NHC
     * 
     * @param Clinic patient NHC
     * @return patient content
     */
    public Patient getPatient(String id) {
        logger.info("Method getPatient");

        // Perform a patient search the id
        Bundle results = client.search()
            .forResource(Patient.class)
            .where(Patient.IDENTIFIER.exactly()
                .code(id))
            .returnBundle(Bundle.class)
            .execute();

        Patient patient = null;
        // Obtain the patient by NHC
        for (BundleEntryComponent entry : results.getEntry()) {
            patient = (Patient) entry.getResource();
        }

        return patient;
    }

    /**
     * Check if the patient has been prescribed in FHIR
     * 
     * @param Clinic patient NHC
     * @return boolean true if is prescribed, false if not
     */
    public boolean getPrescribed(String id) {
        logger.info("Method getPrescribed");

        Bundle observations = client.search()
            .forResource(Observation.class)
            .where(Observation.SUBJECT.hasId(id))
            .where(Observation.CATEGORY.exactly()
                .code(CATEGORY_CODE))
            .where(Observation.CODE.exactly()
                .code("maximum"))
            .returnBundle(Bundle.class)
            .execute();

        if (observations.getEntry()
            .size() > 0)
            return true;
        return false;
    }

    /**
    * Create an HL7 FHIR DocumentReference object 
     * 
     * @param subjectId
     * @param docId
     * @param title
     * @param description
     * @param orgId
     * @param practId
     * @param encounterId
     * @param encodedBytes
     * @return DocumentReference object created
     */
    public DocumentReference createDocumentReference(String subjectId, String docId, String title,
        String description, String orgId, String practId, String encounterId, Coding code, byte[] encodedBytes) {

        DocumentReference docRef = new DocumentReference();

        CodeableConcept docCode = new CodeableConcept();
        
        List<Coding> codings = new ArrayList<Coding>();
        docCode.addCoding(code);
        
        docRef.setType(docCode);
        
        List<Identifier> identifiers = new ArrayList<Identifier>();
        Identifier id = new Identifier();
        id.setSystem(CLINIC);
        id.setValue(docId);
        identifiers.add(id);
        docRef.setIdentifier(identifiers);
        
        // Attach the document
        List<DocumentReferenceContentComponent> contents = 
            new ArrayList<DocumentReferenceContentComponent>();
        
        DocumentReferenceContentComponent contentComp =
            new DocumentReferenceContentComponent();
        
        Attachment attachment = new Attachment();
        attachment.setContentType("application/pdf");
        attachment.setLanguage(language);
        attachment.setTitle(title);
        attachment.setData(encodedBytes);
        
        contentComp.setAttachment(attachment);
        
        contents.add(contentComp);
        docRef.setContent(contents);

        docRef.setId(IdDt.newRandomUuid());
        docRef.setCreated(new Date());
        docRef.setAuthenticator(new Reference(practId));
        docRef.setCustodian(new Reference(orgId));
        docRef.setDescription(description);
        docRef.setDocStatus(ReferredDocumentStatus.FINAL);
        docRef.setSubject(new Reference(subjectId));
        
        if (encounterId != null) {
            DocumentReferenceContextComponent ctxComponent = 
                new DocumentReferenceContextComponent();
            ctxComponent.setEncounter(new Reference(encounterId));
            
            ctxComponent.addEvent().addCoding()
                .setSystem("http://terminology.hl7.org/ValueSet/v3-ActCode")
                .setCode("PHYRHB")
                .setDisplay("Physical Rehab")            ;
            
            CodeableConcept facility = new CodeableConcept();
            facility.addCoding()
                .setSystem("http://hl7.org/fhir/ValueSet/c80-facilitycodes")
                .setCode("80522000")
                .setDisplay("Hospital-rehabilitation");
            ctxComponent.setFacilityType(facility);
            
            CodeableConcept practice = new CodeableConcept();
            practice.addCoding()
                .setSystem("http://hl7.org/fhir/ValueSet/c80-practice-codes")
                .setCode("394602003")
                .setDisplay("Rehabilitation");
            ctxComponent.setPracticeSetting(practice);
            
            docRef.setContext(ctxComponent);
        }
        
        return docRef;
    }


    /**
     * Check if the patient has pdf documents in FHIR
     * 
     * @param Clinic patient NHC
     * @return boolean true if is prescribed, false if not
     */
    public boolean getDocumentReference(String id, String docCode) {

        Bundle documents = client.search()
            .forResource(DocumentReference.class)
            .where(DocumentReference.SUBJECT.hasId(id))
            .where(DocumentReference.IDENTIFIER.exactly()
                .code(docCode))
            .returnBundle(Bundle.class)
            .execute();

        if (documents.getEntry()
            .size() > 0)
            return true;
        return false;
    }
    
    /**
    * Create an HL7 FHIR DocumentReference object 
     * 
     * @param subjectId
     * @param docId
     * @param title
     * @param description
     * @param orgId
     * @param practId
     * @param encounterId
     * @param encodedBytes
     * @return DocumentReference object created
     */
    public Encounter createEncounter(String subjectId, Coding type) {

        Encounter enc = new Encounter();

        Coding code = new Coding();
        code.setSystem("http://terminology.hl7.org/CodeSystem/v3-ActCode");
        code.setCode("AMB");
        code.setDisplay("ambulatory");
        enc.setClass_(code);
        
        enc.addType().addCoding(type);
        
        // PREHAB Code
        Coding prehab = new Coding();
        prehab.setSystem(CLINIC);
        prehab.setDisplay(PREHAB_CODE);
        prehab.setCode(PREHAB_DISPLAY);
        enc.addType().addCoding(prehab);
        
        enc.setId(IdDt.newRandomUuid());
        
        Period period = new Period();
        period.setStart(new Date());
        period.setEnd(new Date());
        
        EncounterStatus status = EncounterStatus.FINISHED;
        enc.setStatus(status);

        return enc;        
    }

    /**
     * Get the Encounter based on the id of the patient and the code inserted
     * 
     * @param Patient id
     * @param Encounter type
     * @return the Encounter if it is found, null if not
     */
    public Encounter getEncounter(String id, String type) {

        Bundle encounters = client.search()
            .forResource(Encounter.class)
            .where(Encounter.SUBJECT.hasId(id))
            .where(Encounter.TYPE.exactly()
                .code(type))
            .returnBundle(Bundle.class)
            .execute();

        Encounter response = null;
        for (BundleEntryComponent entry : encounters.getEntry())
            response = (Encounter) entry.getResource();

        return response;
    }

    /**
     * Get the first Episode Of Care inserted in the patient
     * 
     * @param Patient id
     * @return the EpisodeOfCare if it is found, null if not
     */
    public EpisodeOfCare getFirstEpisode(String id) {

        Bundle episodes = client.search()
            .forResource(EpisodeOfCare.class)
            .where(EpisodeOfCare.PATIENT.hasId(id))
            .returnBundle(Bundle.class)
            .execute();

        EpisodeOfCare response = null;
        for (BundleEntryComponent entry : episodes.getEntry())
            response = (EpisodeOfCare) entry.getResource();

        return response;
    }
}
