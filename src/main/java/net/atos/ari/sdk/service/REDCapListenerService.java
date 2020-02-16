/*
 * Copyright (C) 2020  Atos Spain SA. All rights reserved.
 * 
 * This file is part of the XCare Listener project.
 * 
 * This is free software: you can redistribute it and/or modify it under the 
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
 * XCare Listener Spring boot service
 */
package net.atos.ari.sdk.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.text.ParseException;
import java.text.SimpleDateFormat;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.atos.ari.sdk.model.REDCapItem;
import net.atos.ari.sdk.model.REDCapRecord;

import org.springframework.stereotype.Service;

@Component
public class REDCapListenerService implements ListenerService {

    public static final Logger logger = LoggerFactory.getLogger(REDCapListenerService.class);
    private final static String DATE_FORMAT_PREHAB = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";
    
    /** REDCap base url path. */
    @Value("${redcap.base.url}")
    private String baseUrl;

    /** REDCap token for PAPRIKA. */
    @Value("${redcap.paprika.token}")
    private String token;

    /** REDCap inclusion report instrument for PAPRIKA. */
    @Value("${redcap.paprika.instrument.report.inclusion}")
    private String instrumentInclusion;

    /** REDCap discharge report instrument for PAPRIKA. */
    @Value("${redcap.paprika.instrument.report.discharge}")
    private String instrumentDischarge;

    /** REDCap format for PAPRIKA. */
    @Value("${redcap.paprika.format}")
    private String format;

    /** REDCap token for PAPRIKA. */
    @Value("${redcap.paprika.type}")
    private String type;

    /** REDCap format for PAPRIKA. */
    @Value("${redcap.paprika.xml.format}")
    private String xmlFormat;

    /** REDCap token for PAPRIKA. */
    @Value("${redcap.paprika.xml.type}")
    private String xmlType;    
    
    public Map<String, Object> export() {
        
        List<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new BasicNameValuePair("token", token));
        params.add(new BasicNameValuePair("content", "record"));
        params.add(new BasicNameValuePair("format", format));
        params.add(new BasicNameValuePair("type", type));
        params.add(new BasicNameValuePair("fields", 
            new ArrayList<String>(Arrays.asList("nhc", "record_id")).toString()));
        params.add(new BasicNameValuePair("forms", 
            new ArrayList<String>(Arrays.asList(instrumentInclusion, instrumentDischarge)).toString()));
        params.add(new BasicNameValuePair("rawOrLabel", "raw"));
        params.add(new BasicNameValuePair("rawOrLabelHeaders", "raw"));
        params.add(new BasicNameValuePair("exportCheckboxLabel", "false"));
        params.add(new BasicNameValuePair("exportSurveyFields", "false"));
        params.add(new BasicNameValuePair("exportDataAccessGroups", "false"));
        params.add(new BasicNameValuePair("returnFormat", "json"));
        
        HttpPost post = new HttpPost(baseUrl);
        post.setHeader("Content-Type", "application/x-www-form-urlencoded");

        String result = null;
        int respCode = 200;
        Map <String, Object> response = new HashMap<String, Object>();

        try {
            post.setEntity(new UrlEncodedFormEntity(params));
            HttpClient client = HttpClientBuilder.create()
                .build();
            HttpResponse resp = client.execute(post);
            if (resp != null)
                respCode = resp.getStatusLine()
                    .getStatusCode();

            BufferedReader reader = new BufferedReader(new InputStreamReader(resp.getEntity()
                .getContent()));
            result = reader.lines()
                .collect(Collectors.joining());
        } catch (final Exception e) {
            logger.error(e.getMessage(), e);
            return null;
        }

        JsonParser parser = new JsonParser();
        if (respCode != 200) {
            if (respCode != 301) {
                JsonObject obj = (JsonObject) parser.parse(result);
    
                if (obj.get("error") != null) {
                    String error = obj.get("error")
                        .getAsString();
                    logger.error(error);
                }
            }
            else
                logger.error("Moved Permanently");
            return null;
        }

        JsonArray jsonArray = (JsonArray) parser.parse(result);
       
        //Iterating the contents of the array
        Iterator<JsonElement> iterator = jsonArray.iterator();
        while(iterator.hasNext()) {
            JsonObject jsonObject = iterator.next().getAsJsonObject();
            
            // Instrument fisioterpia_basal complete and locked
            if ( "2".equals(jsonObject.get(instrumentInclusion + "_complete").getAsString()) )
                response.put(jsonObject.get("record_id").getAsString(),
                    jsonObject.get("nhc").getAsString());
        }

        return response;
    }

    public boolean exportPDF(String id, String instrument) {
        logger.info("REDCapId: {} Instrument: {}", id, instrument);
        
        List<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new BasicNameValuePair("token", token));
        params.add(new BasicNameValuePair("content", "pdf"));
        params.add(new BasicNameValuePair("record", id));
        params.add(new BasicNameValuePair("instrument", instrument));
        params.add(new BasicNameValuePair("returnFormat", "json"));
        
        HttpPost post = new HttpPost(baseUrl);
        post.setHeader("Content-Type", "application/x-www-form-urlencoded");

        String result = null;
        int respCode = 200;

        try {
            post.setEntity(new UrlEncodedFormEntity(params));
            HttpClient client = HttpClientBuilder.create()
                .build();
            HttpResponse resp = client.execute(post);
            if (resp != null)
                respCode = resp.getStatusLine()
                    .getStatusCode();
            
            if (respCode != 200)
                return false;
            
            InputStream is = resp.getEntity().getContent();
            FileOutputStream fos = new FileOutputStream(new File("export.pdf"));
            int read;
            final byte[] buf = new byte[4096];
            while ((read = is.read(buf)) > 0)
                fos.write(buf, 0, read);
            fos.close(); is.close();
            return true;
        } catch (final Exception e) {
            logger.error(e.getMessage(), e);
            return false;
        }
    }   
}