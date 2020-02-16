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

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.maven.model.Organization;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.dstu3.model.Coding;
import org.hl7.fhir.dstu3.model.DateTimeType;
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

    private final String LOINC = "http://loinc.org";
    private final String UCUM = "http://unitsofmeasure.org";
    private final String LOINC_CODE = "55423-8";
    private final String LOINC_DISPLAY = "Number of steps in unspecified time Pedometer";
    private final String LOINC_TEXT = "Step Count";
    private final String UCUM_UNITS = "steps/day";
    private final String UCUM_UNITS_CODE = "{steps}/d";

    private final String CATEGORY = "https://www.hl7.org/fhir/valueset-observation-category.html";
    private final String CATEGORY_CODE = "activity";
    private final String CATEGORY_DISPLAY = "Activity";

    /** FHIR base url path. */
    @Value("${fhir.base.url}")
    private String fhirUrl;

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

        ctx.newXmlParser()
            .setPrettyPrint(true)
            .encodeResourceToString(bundle);

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
            .limitTo(1000)
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
    * Create an HL7 FHIR Observation object 
     * 
     * @param subjectId
     * @param val
     * @param Date start
     * @param Date end
     * @param boolean prescribe
     * @return Observation object created
     */
    public Observation createObservation(String subjectId, Double val, 
        Date start, Date end, boolean prescribe) {
        Observation obs = new Observation();

        obs.addCategory()
            .addCoding()
            .setSystem(CATEGORY)
            .setCode(CATEGORY_CODE)
            .setDisplay(CATEGORY_DISPLAY);

        obs.getCode()
            .addCoding()
            .setSystem(LOINC)
            .setCode(LOINC_CODE)
            .setDisplay(LOINC_DISPLAY);
        obs.getCode()
            .setText(LOINC_TEXT);

        if (prescribe)
            obs.getCode()
                .addCoding()
                .setSystem("http://hl7.org/fhir/observation-statistics")
                .setCode("maximum")
                .setDisplay("Maximum");

        if (val != null) {
            Quantity value = new Quantity();
            value.setSystem(UCUM)
                .setUnit(UCUM_UNITS)
                .setCode(UCUM_UNITS_CODE)
                .setValue(val);

            obs.setValue(value);
        }
        obs.setId(IdDt.newRandomUuid());

        Date today = null;
        if (start == null)
            today = new Date();
        else
            today = start;

        Period period = new Period();
        period.setStart(today);
        if (end != null)
            period.setEnd(end);

        obs.setEffective(period);

        if (prescribe)
            obs.setStatus(ObservationStatus.REGISTERED);
        else
            obs.setStatus(ObservationStatus.FINAL);

        obs.setSubject(new Reference(subjectId));

        return obs;
    }

    /**
     * Get an HL7 FHIR Practitioner object given the Id
     * 
     * @param Practitioner Id SAP
     * @return Practitioner content
     */
    public Practitioner getPractitioner(String id) {

        // Perform a search of the organization given the id
        Bundle results = client.search()
            .forResource(Practitioner.class)
            .where(Practitioner.IDENTIFIER.exactly()
                .code(id))
            .returnBundle(Bundle.class)
            .execute();

        Practitioner pra = null;
        // Obtain the Practitioner by Id
        for (BundleEntryComponent entry : results.getEntry()) {
            pra = (Practitioner) entry.getResource();
            logger.info("Practitioner found: {}", 
                pra.getNameFirstRep().getGivenAsSingleString());
        }
        return pra;
    }

}
