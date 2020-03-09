package net.atos.ari.sdk.client;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

import org.hl7.fhir.dstu3.model.Encounter;
import org.hl7.fhir.dstu3.model.Enumerations.AdministrativeGender;
import org.hl7.fhir.dstu3.model.EpisodeOfCare;
import org.hl7.fhir.dstu3.model.Identifier;
import org.hl7.fhir.dstu3.model.Patient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ws.client.core.support.WebServiceGatewaySupport;

import net.atos.ari.sdk.oru.ACK;
import net.atos.ari.sdk.oru.ORUR01;
import net.atos.ari.sdk.oru.ORUR01.MSH;
import net.atos.ari.sdk.oru.ORUR01.MSH.MSH11;
import net.atos.ari.sdk.oru.ORUR01.MSH.MSH12;
import net.atos.ari.sdk.oru.ORUR01.MSH.MSH4;
import net.atos.ari.sdk.oru.ORUR01.MSH.MSH5;
import net.atos.ari.sdk.oru.ORUR01.MSH.MSH7;
import net.atos.ari.sdk.oru.ORUR01.MSH.MSH9;
import net.atos.ari.sdk.oru.ORUR01.ORUR01PATIENTRESULT;
import net.atos.ari.sdk.oru.ORUR01.ORUR01PATIENTRESULT.ORUR01ORDEROBSERVATION;
import net.atos.ari.sdk.oru.ORUR01.ORUR01PATIENTRESULT.ORUR01ORDEROBSERVATION.OBR;
import net.atos.ari.sdk.oru.ORUR01.ORUR01PATIENTRESULT.ORUR01ORDEROBSERVATION.OBR.OBR2;
import net.atos.ari.sdk.oru.ORUR01.ORUR01PATIENTRESULT.ORUR01ORDEROBSERVATION.OBR.OBR3;
import net.atos.ari.sdk.oru.ORUR01.ORUR01PATIENTRESULT.ORUR01ORDEROBSERVATION.OBR.OBR7;
import net.atos.ari.sdk.oru.ORUR01.ORUR01PATIENTRESULT.ORUR01ORDEROBSERVATION.ORUR01COMMONORDER;
import net.atos.ari.sdk.oru.ORUR01.ORUR01PATIENTRESULT.ORUR01ORDEROBSERVATION.ORUR01COMMONORDER.ORC;
import net.atos.ari.sdk.oru.ORUR01.ORUR01PATIENTRESULT.ORUR01ORDEROBSERVATION.ORUR01COMMONORDER.ORC.ORC12;
import net.atos.ari.sdk.oru.ORUR01.ORUR01PATIENTRESULT.ORUR01ORDEROBSERVATION.ORUR01COMMONORDER.ORC.ORC2;
import net.atos.ari.sdk.oru.ORUR01.ORUR01PATIENTRESULT.ORUR01ORDEROBSERVATION.ORUR01COMMONORDER.ORC.ORC3;
import net.atos.ari.sdk.oru.ORUR01.ORUR01PATIENTRESULT.ORUR01ORDEROBSERVATION.ORUR01COMMONORDER.ORC.ORC9;
import net.atos.ari.sdk.oru.ORUR01.ORUR01PATIENTRESULT.ORUR01ORDEROBSERVATION.ORUR01OBSERVATION;
import net.atos.ari.sdk.oru.ORUR01.ORUR01PATIENTRESULT.ORUR01ORDEROBSERVATION.ORUR01OBSERVATION.OBX;
import net.atos.ari.sdk.oru.ORUR01.ORUR01PATIENTRESULT.ORUR01ORDEROBSERVATION.ORUR01OBSERVATION.OBX.OBX19;
import net.atos.ari.sdk.oru.ORUR01.ORUR01PATIENTRESULT.ORUR01ORDEROBSERVATION.ORUR01OBSERVATION.OBX.OBX3;
import net.atos.ari.sdk.oru.ORUR01.ORUR01PATIENTRESULT.ORUR01ORDEROBSERVATION.ORUR01OBSERVATION.OBX.OBX5;
import net.atos.ari.sdk.oru.ORUR01.ORUR01PATIENTRESULT.ORUR01PATIENT;
import net.atos.ari.sdk.oru.ORUR01.ORUR01PATIENTRESULT.ORUR01PATIENT.ORUR01VISIT;
import net.atos.ari.sdk.oru.ORUR01.ORUR01PATIENTRESULT.ORUR01PATIENT.ORUR01VISIT.PV1;
import net.atos.ari.sdk.oru.ORUR01.ORUR01PATIENTRESULT.ORUR01PATIENT.ORUR01VISIT.PV1.PV119;
import net.atos.ari.sdk.oru.ORUR01.ORUR01PATIENTRESULT.ORUR01PATIENT.PID;
import net.atos.ari.sdk.oru.ORUR01.ORUR01PATIENTRESULT.ORUR01PATIENT.PID.PID3;
import net.atos.ari.sdk.oru.ORUR01.ORUR01PATIENTRESULT.ORUR01PATIENT.PID.PID3.CX4;
import net.atos.ari.sdk.oru.ORUR01.ORUR01PATIENTRESULT.ORUR01PATIENT.PID.PID5;
import net.atos.ari.sdk.oru.ORUR01.ORUR01PATIENTRESULT.ORUR01PATIENT.PID.PID5.XPN1;
import net.atos.ari.sdk.oru.ORUR01.ORUR01PATIENTRESULT.ORUR01PATIENT.PID.PID6;
import net.atos.ari.sdk.oru.ORUR01.ORUR01PATIENTRESULT.ORUR01PATIENT.PID.PID7;

public class Client extends WebServiceGatewaySupport {

    private static final Logger logger = LoggerFactory.getLogger(Client.class);

    private final static String DATE_FORMAT_ORU = "yyyyMMddHHmmss";
    private final static String DATE_FORMAT_BIRTHDATE = "yyyyMMdd";
    

    private static final String SOURCE_CLINIC = "http://clinic.cat";
    private static final String SOURCE_ORU = "http://orusap.org";
    private static final String SOURCE_EPISODE = "ANE";
    private static final String SOURCE_CIP = "http://cip.org";
    private static final String SOURCE_SAP_ORDER = "HCPB";
    // private static final String SOURCE_SAP_ORDER = "http://sap-internal.org";

    @Value("${his.doctor.id}")
    private String defaultDoctorId;
    
    public ORUR01 createORU(Patient fhirPatient, 
        Encounter encounter, EpisodeOfCare episodeOfCare, String reportName) {
        

        String sapId = "", cip = "", episode = "", placeOrder = "", nhcPatient = "";
        if (episodeOfCare != null)
            for (Identifier mpi : episodeOfCare.getIdentifier()) {
                if (SOURCE_EPISODE.equalsIgnoreCase(mpi.getSystem()) == true)
                    episode = mpi.getValue();
            }
        
        for (Identifier mpi : fhirPatient.getIdentifier()) {
            if (SOURCE_ORU.equalsIgnoreCase(mpi.getSystem()) == true)
                sapId = mpi.getValue();
            if (SOURCE_CIP.equalsIgnoreCase(mpi.getSystem()) == true)
                cip = mpi.getValue();
            if (SOURCE_CLINIC.equalsIgnoreCase(mpi.getSystem()) == true)
                nhcPatient = mpi.getValue();
        }

        if (encounter != null)
            for (Identifier mpi : encounter.getIdentifier()) {
                if (SOURCE_SAP_ORDER.equalsIgnoreCase(mpi.getSystem()) == true)
                    placeOrder = mpi.getValue();
            }

        ORUR01 oruObject = new ORUR01();

        MSH msh = new MSH();
        oruObject.setMSH(msh);

        // Field Separator
        oruObject.getMSH()
            .setMSH1("|");

        // Encoding characters
        oruObject.getMSH()
            .setMSH2("^~\\&amp;amp;");

        // Sending Facility
        MSH4 msh4 = new MSH4();
        msh4.setHD1("ATOS");
        ;
        oruObject.getMSH()
            .setMSH4(msh4);

        // Receiving application
        MSH5 msh5 = new MSH5();
        msh5.setHD1("SAPCLINIC");

        oruObject.getMSH()
            .setMSH5(msh5);

        // Get current date
        Date dateToday = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT_ORU);
        String strToday = sdf.format(dateToday);

        // Date/Time Message
        MSH7 msh7 = new MSH7();
        msh7.setTS1(strToday);
        ;
        oruObject.getMSH()
            .setMSH7(msh7);

        // Message Type: Code, Trigger Event and Structure
        MSH9 msh9 = new MSH9();
        msh9.setMSG1("ORU");
        msh9.setMSG2("R01");
        msh9.setMSG3("ORU_R01");
        oruObject.getMSH()
            .setMSH9(msh9);

        // TODO
        // Auto-generate this number
        
        // Message Control ID
        oruObject.getMSH()
            .setMSH10("32093232323223");

        // Processing ID
        MSH11 msh11 = new MSH11();
        msh11.setPT1("D");
        ;
        oruObject.getMSH()
            .setMSH11(msh11);

        // Version ID: 2.5
        MSH12 msh12 = new MSH12();
        msh12.setVID1("2.5");
        ;
        oruObject.getMSH()
            .setMSH12(msh12);

        ORUR01PATIENTRESULT patientResult = new ORUR01PATIENTRESULT();
        ORUR01PATIENT patient = new ORUR01PATIENT();
        PID pid = new PID();

        // Patient Identifier List

        // NHC
        PID3 nhc = new PID3();
        nhc.setCX1(nhcPatient);
        CX4 cx4 = new CX4();
        cx4.setHD1("HIS");
        nhc.setCX4(cx4);
        nhc.setCX5("P");
        pid.getPID3()
            .add(nhc);

        // CIP
        PID3 pid3 = new PID3();
        pid3.setCX1(cip);
        CX4 cx4cip = new CX4();
        cx4cip.setHD1("ES-CT");
        pid3.setCX4(cx4cip);
        pid3.setCX5("JHN");
        pid.getPID3()
            .add(pid3);

        String name = fhirPatient.getNameFirstRep()
            .getGivenAsSingleString();
        String surName = fhirPatient.getNameFirstRep()
            .getFamily();
        
        // Patient Name
        PID5 pid5 = new PID5();
        XPN1 xpn1 = new XPN1();
        
        // Surname
        xpn1.setFN1(surName);
        pid5.setXPN1(xpn1);
        
        // Name
        pid5.setXPN2(name);
        pid.setPID5(pid5);

        // Patient Second Surname
        PID6 pid6 = new PID6();
        ORUR01.ORUR01PATIENTRESULT.ORUR01PATIENT.PID.PID6.XPN1 xpn1p = 
            new ORUR01.ORUR01PATIENTRESULT.ORUR01PATIENT.PID.PID6.XPN1();
        xpn1p.setFN1("");
        pid6.setXPN1(xpn1p);
        pid.setPID6(pid6);

        Date birthDate = fhirPatient.getBirthDate();
        sdf = new SimpleDateFormat(DATE_FORMAT_BIRTHDATE);
        String strBirthDate = sdf.format(birthDate);
        
        // Birth Date
        PID7 pid7 = new PID7();
        pid7.setTS1(strBirthDate);
        pid.setPID7(pid7);

        String sex = "3";
        if (fhirPatient.getGender() == AdministrativeGender.MALE )
            sex = "0";
        else if (fhirPatient.getGender() == AdministrativeGender.FEMALE)
            sex = "1";
        
        // Sex: 0 Man 1 Woman
        pid.setPID8(sex);
        patient.setPID(pid);

        // Visit
        ORUR01VISIT visit = new ORUR01VISIT();
        PV1 pv1 = new PV1();
        pv1.setPV12("O");

        // Episode
        PV119 pv119 = new PV119();
        pv119.setCX1(episode);
        // pv119.setCX1("episodi");
        pv1.setPV119(pv119);
        visit.setPV1(pv1);
        patient.setORUR01VISIT(visit);

        patientResult.setORUR01PATIENT(patient);

        // Order
        ORUR01ORDEROBSERVATION order = new ORUR01ORDEROBSERVATION();
        ORUR01COMMONORDER common = new ORUR01COMMONORDER();

        // Order Control
        ORC orc = new ORC();
        orc.setORC1("NW");

        // Placer Order Number
        ORC2 orc2 = new ORC2();
        orc2.setEI2(placeOrder);
        orc.setORC2(orc2);

        // Filler Order Number
        ORC3 orc3 = new ORC3();
        orc3.setEI2(placeOrder);
        orc.setORC3(orc3);

        // Observation Date/Time
        ORC9 orc9 = new ORC9();
        orc9.setTS1(strToday);
        orc.setORC9(orc9);

        String service = fhirPatient.getManagingOrganization()
            .getDisplay();
        
        // Service
        ORC12 orc12 = new ORC12();
        orc12.setXCN1(service);
        orc.setORC12(orc12);
        common.setORC(orc);
        order.setORUR01COMMONORDER(common);

        // Requested Observation
        OBR obr = new OBR();

        // Place Order Number
        OBR2 obr2 = new OBR2();
        obr2.setEI2(sapId);
        obr.setOBR2(obr2);

        // Filler Order Number
        OBR3 obr3 = new OBR3();
        obr3.setEI2(sapId);
        obr.setOBR3(obr3);

        // Observation Date/Time
        OBR7 obr7 = new OBR7();
        obr7.setTS1(strToday);
        obr.setOBR7(obr7);

        order.setOBR(obr);

        // Complete Report
        ORUR01OBSERVATION observation = new ORUR01OBSERVATION();
        OBX obx = new OBX();

        // Value Type
        obx.setOBX2("ED");

        // Observation Identifier
        OBX3 obx3 = new OBX3();
        obx3.setCE1("MODALITAT DEL DOCUMENT");
        
        // Name of the document
        obx3.setCE2(reportName);
        obx.setOBX3(obx3);

        // Observation Value
        OBX5 obx5 = new OBX5();
        obx5.setED2("MULTIPART");
        obx5.setED3("PDF");
        obx5.setED4("BASE64");

        obx.setOBX5(obx5);

        // Observation status - F - Final, C - Corrected
        // To create the document do not fill the field 
        // obx.setOBX11("");

        // Producer's Reference
        obx.setOBX15("");

        String doctorSapId = defaultDoctorId;
        if (fhirPatient.getGeneralPractitionerFirstRep() != null)
            doctorSapId = fhirPatient.getGeneralPractitionerFirstRep()
                .getDisplay();
        
        // Responsible Observer
        obx.setOBX16(doctorSapId);

        // Date/Time of the Analysis
        OBX19 obx19 = new OBX19();
        obx19.setTS1(strToday);
        obx.setOBX19(obx19);

        observation.setOBX(obx);
        order.setORUR01OBSERVATION(observation);
        patientResult.setORUR01ORDEROBSERVATION(order);

        oruObject.setORUR01PATIENTRESULT(patientResult);
        
        return oruObject;
    }

    @SuppressWarnings("unchecked")
    public ACK setORU(Patient patient, String reportName, 
        Encounter encounter, EpisodeOfCare episode, byte[] encodedBytes) {
        
        ORUR01 oruObject = createORU(patient, encounter, episode, reportName);

        String pdfInBase64 = new String(encodedBytes);

        OBX5 obx5 = oruObject.getORUR01PATIENTRESULT()
            .getORUR01ORDEROBSERVATION().getORUR01OBSERVATION().getOBX().getOBX5();
        obx5.setED5(pdfInBase64);
        
        ACK response = (ACK) getWebServiceTemplate().marshalSendAndReceive(oruObject);

        return response;
    }
}
