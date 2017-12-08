// $Id$
package com.servicedeskplus.integration;

import com.manageengine.servicedesk.actionplugin.executor.DefaultActionInterface;
import com.manageengine.servicedesk.actionplugin.executor.ExecutorData;
import com.manageengine.servicedesk.actionplugin.ActionExecutorConstant;
import com.adventnet.servicedesk.server.utils.SDDataManager;
import com.adventnet.servicedesk.utils.ServiceDeskUtil;
import com.manageengine.servicedesk.utils.CommonUtil;
import com.adventnet.servicedesk.common.DateTime;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.*;

import java.io.File;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.w3c.dom.Element;
import sun.misc.BASE64Encoder;

public class JiraActionImplementation extends DefaultActionInterface
{

    private static Logger logger = Logger.getLogger(JiraActionImplementation.class.getName());

    public JSONObject execute(ExecutorData executorData) throws Exception
    {
        ExecutorData data = executorData;

        HashMap menuMap = data.getAdditionalProps();
        String menuName = (String)menuMap.get("MENUNAME");
        JSONObject json = new JSONObject();
        if(menuMap.containsKey("HTML_LOAD"))
        {
            try{
                JSONObject dataJson=data.getHtmlDataJson();
                String html_Load=(String)menuMap.get("HTML_LOAD");
                String xmlPath = System.getProperty("user.dir") + File.separator + ".." + File.separator + "integration" + File.separator + "conf" + File.separator + "Jira.xml";// No I18N
                Document jiraDoc = null;
                jiraDoc = CommonUtil.getInstance().getXmlDocument(xmlPath);
                
                String query = "menus>menu#name="+menuName+">request";// No I18N
                JSONObject configurationJson = getJiraConfigurations(jiraDoc, query);    //get configurations from Admp.xml
                configurationJson=configurationJson.getJSONObject("request");
                String url = (String) configurationJson.get("url");
                String userName = (String) configurationJson.get("username");
                String password = (String) configurationJson.get("password");
                BASE64Encoder enc = new BASE64Encoder();  
                String userpassword = userName + ":" + password;// No I18N
                String encodedAuthorization = new sun.misc.BASE64Encoder().encode(userpassword.getBytes());
                String authorizationData="Basic " + encodedAuthorization;// No I18N
                HashMap hmpConnectionDetails = new HashMap();
                hmpConnectionDetails.put("authorization",authorizationData);
                hmpConnectionDetails.put("requestType", "GET");
                hmpConnectionDetails.put("Content-Type", "application/json");
                boolean ishttps = false;
                if (url.toLowerCase().startsWith("https:")) {
                    ishttps = true;
                }
                hmpConnectionDetails.put("ishttps", ishttps);
                String action = dataJson.getString("OPERATION");
                String result;
                if(action.equals("GetProjects")){
                    String projectUrl=url.replace("issue","project");//NO I18N
                    hmpConnectionDetails.put("url", projectUrl);// No I18N
                    result=(String) CommonUtil.getInstance().getResponseObject(hmpConnectionDetails);
                    try{
                        JSONArray projects=new JSONArray(result);
                        json.put("result",projects);                        
                    }
                    catch(Exception e){
                        logger.log(Level.INFO,"result of GetProjects operation:::::::::"+ result);
                        json.put("failure","Issue in getting project list form JIRA, please check the log file");// No I18N
                        e.printStackTrace();
                    }
                }
                else if(action.equals("GetFields"))
                {
                    String projectId=dataJson.getString("PROJECTID");
                    hmpConnectionDetails.put("url", url + "createmeta?expand=projects.issuetypes.fields"+"&projectIds="+projectId);// No I18N
                    result=(String) CommonUtil.getInstance().getResponseObject(hmpConnectionDetails);
                    if(configurationJson.has("html_fields")){
                        String htmlFields=configurationJson.getString("html_fields");
                        if(!"".equals(htmlFields))
                        {
                            JSONObject htmlFieldsJSON = new JSONObject(htmlFields);
                            if(htmlFieldsJSON.has("field")){
                                Object fields=htmlFieldsJSON.get("field");
                                if(fields instanceof JSONArray){
                                    json.put("htmlFields", htmlFieldsJSON.getJSONArray("field"));
                                }
                                else if(fields instanceof JSONObject){
                                   JSONObject field=htmlFieldsJSON.getJSONObject("field");
                                   JSONArray fieldsArr=new JSONArray();
                                   fieldsArr.put(field);
                                   json.put("htmlFields",fieldsArr);
                                }
                            }
                            else{
                                json.put("htmlFields", new JSONArray());
                            }
                        }
                        else{
                            json.put("htmlFields", new JSONArray());
                        }
                    }
                    else{
                        json.put("htmlFields",new JSONArray());
                    }
                    if(configurationJson.has("mapping_fields")){
                        String mappingFields=configurationJson.getString("mapping_fields");
                        if(!"".equals(configurationJson.getString("mapping_fields")))
                        {
                            JSONObject mappingFieldsJSON = new JSONObject(mappingFields);
                            if(mappingFieldsJSON.has("field")){
                                Object fields=mappingFieldsJSON.get("field");
                                if(fields instanceof JSONArray){
                                    json.put("mappingFields", mappingFieldsJSON.getJSONArray("field"));
                                }
                                else if(fields instanceof JSONObject){
                                   JSONObject field=mappingFieldsJSON.getJSONObject("field");
                                   JSONArray fieldsArr=new JSONArray();
                                   fieldsArr.put(field);
                                   json.put("mappingFields",fieldsArr);
                                }
                            }
                            else{
                                json.put("mappingFields", new JSONArray());
                            }
                        }
                        else{
                            json.put("mappingFields", new JSONArray());
                        }
                    }
                    else{
                        json.put("mappingFields", new JSONObject());
                    }
                    json.put("fields",new JSONObject(result));
                }
                else if(action.equals("SaveTicket"))
                {
                    
                    String ticketData=dataJson.getString("TicketData");
                    JSONObject jiraRequestJson=new JSONObject(ticketData);
                    dataJson.put("TicketData",jiraRequestJson);
                    hmpConnectionDetails.put("url", url);
                    hmpConnectionDetails.put("content", dataJson.getString("TicketData"));
                    result=(String) CommonUtil.getInstance().getResponseObject(hmpConnectionDetails);
                    try {
                        json.put("result",new JSONObject(result));
                    } catch (Exception e) {
                        logger.log(Level.INFO,"result of SaveTicket operation:::::::::"+ result);
                        json.put("failure","Issue in creating ticket in JIRA, please check the log file");// No I18N
                        e.printStackTrace();
                    }
                }
            }
            catch (Exception ex) {
                   logger.log(Level.INFO,"Exception caught in JiraActionImplementation");
                   json.put("failure","Check Jira Xml file configurations and Jira server connectivity, please check the log file");// No I18N
                   ex.printStackTrace();
            }            
        }
        else
        {
            JSONObject requestJsonObj=data.getDataJSON();
            HashMap completeDetailsMap = readXMLFile(menuName);
            HashMap authenticationDetails = (HashMap) completeDetailsMap.get("authenticationDetails");
            HashMap requestDetailsMap = (HashMap) completeDetailsMap.get("requestDetailsMap");
            HashMap customFieldsTypeMap = (HashMap) completeDetailsMap.get("customFieldsTypeMap");

            HashMap detailsMap = new HashMap();

            Iterator iter = requestDetailsMap.keySet().iterator();
            while (iter.hasNext())
            {
                String key = (String) iter.next();
                String val = (String) requestDetailsMap.get(key);
                if (val.contains("$"))
                {
                    val = val.replace("$", "");
                    if (requestJsonObj.has(val))
                    {
                        val = (String) requestJsonObj.get(val);
                        detailsMap.put(key.trim(), val.trim());
                    }
                }
                else if (!"".equals(val) && val != null)
                {
                    detailsMap.put(key.trim(), val.trim());
                }
            }
            /*
            Here we are iterating the requestDetailsMap putting these details in detailsMap HashMap
            If the requestDetailsMap value contains "$" symbol followed by the key, we need to get appropritave key value from the requestJsonObj JSONObject
             */
            logger.log(Level.INFO, "detailsMap:::::::::" + detailsMap);

            String jiraResult = null;
            String jiraURL = (String) authenticationDetails.get("url");

            // if jira url contains any $val, it means for getting latest jira information
            if(jiraURL.contains("$")){
                    String [] url = jiraURL.split("\\$");
                    jiraURL = replaceURL(jiraURL,requestJsonObj);
                    logger.log(Level.INFO,"jiraURL:::::::::"+ jiraURL);
                    if(jiraURL.equals(url[0])){
                            json.put("result","failure");
                            HashMap successNFailureMap = (HashMap) completeDetailsMap.get("successNFailureMesgMap");
                            String failureMesg = (String) successNFailureMap.get("failureMesg");	
                            json.put("message",failureMesg);
                            logger.log(Level.INFO,"return json:::::::::"+ json);
                            return json;

                    }
                    else{

                            jiraResult = getResultantFromExtApp(jiraURL,"GET",null,authenticationDetails);// No I18N
                            json = constructUpdateJiraJSONObject(jiraResult,completeDetailsMap);
                            logger.log(Level.INFO,"return json:::::::::"+ json);
                            return json;
                    }
            }

            else
            {


            String jsonString = constructJson(detailsMap, requestJsonObj, customFieldsTypeMap);
            /*
            Here we need to construct the json before submitting to jira based on the detailsMap HashMap key and Value
            A Simple constructed json as follows:
            {
            "fields":{
            "summary":"Simple SCP to JIRA Integration",
            "issuetype":{
            "name":"Bug"
            },
            "project":{
            "key":"SCP"
            },
            "priority":{
            "name":"Major"
            }
            }
            }
            **/
            logger.log(Level.INFO, "jsonString:::::::::" + jsonString);


            jiraResult = submitDatatoExtApp(jsonString, authenticationDetails);
            logger.log(Level.INFO, "jiraResult:::::::::" + jiraResult);

            /* Sample jiraResult as Follows if it is success
            {
            "id":"11300",
            "key":"SCP-73",
            "self":"http://seshadri-0040:8080/rest/api/2/issue/11300"
            }
             */

            // jiraResult will be null in case if it is failure

            /* Here we need to construct return JSONObject for updating someFields in SDP or Adding notes based on XML Entries
            Simple ReturnedJSONObject as follows
            1. If it is success
            {
            "message":"Ticket is created in jira with key : SDP-84 And with Id: 11406",
            "result":"success",
            "operation":[
            {
            "INPUT_DATA":[
            {
            "notes":{
            "notesText":"Ticket is created in jira with issueID : 11406"
            }
            }
            ],
            "OPERATIONNAME":"ADD_NOTE"
            },
            {
            "INPUT_DATA":[
            {
            "JIRA_ISSUE_KEY":"SDP-84",
            "JIRA_ISSUE_ID":"11406"
            }
            ],
            "OPERATIONNAME":"EDIT_REQUEST"
            }
            ]
            }

            2.	If it is failed
            {
            "message":"Failed to Integrate to jira",
            "result":"failure"
            }
            **/

            json = constructReturnedJSONObject(jiraResult, completeDetailsMap);
            logger.log(Level.INFO, "return json:::::::::" + json);
            
            }
        }
        return json;
    }

    private String submitDatatoExtApp(String data, HashMap authenticationDetails) throws Exception
    {
        String requestURL = (String) authenticationDetails.get("url");
        return getResultantFromExtApp(requestURL, "POST", data, authenticationDetails); // No I18N
    }

    private String constructJson(Map<String, String> params, JSONObject requestJsonObj, HashMap customFieldsTypeMap) throws Exception
    {
        JSONObject json = new JSONObject();
        try
        {
            HashMap fields = new HashMap();
            Iterator iter = params.keySet().iterator();
            while (iter.hasNext())
            {
                String key = (String) iter.next();
                String val = (String) params.get(key);
                String type = (String) customFieldsTypeMap.get(key);
                if ("projectpicker".equalsIgnoreCase(type))
                {
                    HashMap typeMap = new HashMap();
                    if (!"".equals(val) && val != null)
                    {
                        typeMap.put("key", val.trim());
                        fields.put(key.trim(), typeMap);
                    }
                }
                else if ("select".equalsIgnoreCase(type))
                {
                    HashMap typeMap = new HashMap();
                    if (!"".equals(val) && val != null)
                    {
                        if ("issuetype".equalsIgnoreCase(key) || "priority".equalsIgnoreCase(key) || "reporter".equalsIgnoreCase(key))
                        {
                            typeMap.put("name", val.trim());
                        }
                        else
                        {
                            typeMap.put("value", val.trim());
                        }
                        fields.put(key.trim(), typeMap);
                    }
                }
                else if ("float".equalsIgnoreCase(type))
                {
                    if (!"".equals(val) && val != null)
                    {
                        Long floatValue = null;
                        try
                        {
                            floatValue = Long.parseLong(val);
                        }
                        catch (Exception e)
                        {
                            e.printStackTrace();
                        }
                        if (floatValue != null)
                        {
                            fields.put(key.trim(), floatValue);
                        }
                    }
                }
                else if ("datepicker".equalsIgnoreCase(type))
                {
                    if (!"".equals(val) && val != null)
                    {
                        if (val != null && !val.equals("0") && !val.equals("-1"))
                        {
                            Date reqDate = new Date((new Long(val)).longValue());
                            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd"); // No I18N
                            val = format.format(reqDate);
                            fields.put(key.trim(), val.trim());
                        }
                    }
                }
                else if ("userpicker".equalsIgnoreCase(type) || "grouppicker".equalsIgnoreCase(type))
                {
                    if (!"".equals(val) && val != null)
                    {
                        HashMap typeMap = new HashMap();
                        typeMap.put("name", val.trim());
                        fields.put(key.trim(), typeMap);
                    }
                }
                else if ("textfield".equalsIgnoreCase(type) || "textarea".equalsIgnoreCase(type) || "url".equalsIgnoreCase(type))
                {
                    fields.put(key.trim(), val.trim());
                }
                else if("requestURL".equalsIgnoreCase(type))	 	
                {	 	
                        val = ServiceDeskUtil.getInstance().getScheme()+"://"+ServiceDeskUtil.getInstance().getSDWebUrl() + "/WorkOrder.do?woMode=viewWO&woID="+val+"&&fromListView=true";	 	
                        fields.put(key.trim(), val.trim());	 	
                }
                else if ("datetime".equalsIgnoreCase(type))
                {
                    if (!"".equals(val) && val != null)
                    {
                        String userFormat = (String) requestJsonObj.get("userTimeFormat");
                        long timeinmillisecs = DateTime.dateInLong(val, userFormat);
                        //YYYY-MM-DDThh:mm:ss.sTZD
                        //val = DateTime.longdateToString(timeinmillisecs,"yyyy-MM-dd");
                        //fields.put(key.trim(),val.trim());
                    }
                }
                else if ("labels".equalsIgnoreCase(type))
                {
                    if (!"".equals(val) && val != null)
                    {
                        String[] strArray = val.split(",");
                        ArrayList addMap = new ArrayList();
                        for (int z = 0; z < strArray.length; z++)
                        {
                            addMap.add(strArray[z]);
                        }
                        fields.put(key.trim(), addMap);
                    }
                }
                else if ("multiselect".equalsIgnoreCase(type) || "multigrouppicker".equalsIgnoreCase(type) || "multiuserpicker".equalsIgnoreCase(type) || "multiversion".equalsIgnoreCase(type))
                {
                    if (!"".equals(val) && val != null)
                    {
                        String[] strArray = val.split(",");
                        ArrayList addMap = new ArrayList();
                        for (int z = 0; z < strArray.length; z++)
                        {
                            HashMap multiSelectMap = new HashMap();
                            if ("multiuserpicker".equalsIgnoreCase(type) || "multigrouppicker".equalsIgnoreCase(type))
                            {
                                    multiSelectMap.put("name", strArray[z]);
                            }
                            else
                            {
                                    multiSelectMap.put("value", strArray[z]);
                            }
                            addMap.add(multiSelectMap);							
                        }
                        fields.put(key.trim(),addMap); 
                    }
                }
                else if ("radiobuttons".equalsIgnoreCase(type))
                {
                    if (!"".equals(val) && val != null)
                    {
                        HashMap typeMap = new HashMap();
                        typeMap.put("id", val.trim());
                        fields.put(key.trim(), typeMap);
                    }
                }
                else if ("cascadingselect".equalsIgnoreCase(type))
                {
                }
            }
            try
            {
                json.accumulate("fields", fields); // No I18N
            }
            catch (JSONException e)
            {
                e.printStackTrace();
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        return json.toString();
    }

    private String getResultantFromExtApp(String requestURL, String methodType, String data, HashMap authenticationDetails) throws Exception
    {
        String resultant = null;
        String userName = (String) authenticationDetails.get("username");
        String passWord = (String) authenticationDetails.get("password");
        logger.log(Level.INFO, "requestURL:::::::::" + requestURL);
        try
        {
            URL url = new URL(requestURL);
            BASE64Encoder enc = new BASE64Encoder();

            String userpassword = userName + ":" + passWord;
            String encodedAuthorization = new sun.misc.BASE64Encoder().encode(userpassword.getBytes());
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoOutput(true);
            if (methodType.equals("POST"))
            {
                connection.setRequestMethod("POST"); // No I18N
            }
            else if (methodType.equals("GET"))
            {
                connection.setRequestMethod("GET"); // No I18N
            }

            connection.setRequestProperty("Content-Type", "application/json"); // No I18N
            connection.setRequestProperty("Authorization", "Basic " + encodedAuthorization); // No I18N
            if (methodType.equals("POST"))
            {
                connection.setUseCaches(false);
                connection.setAllowUserInteraction(false);
                OutputStream out = connection.getOutputStream();
                Writer write = new OutputStreamWriter(out, "UTF-8"); // No I18N
                write.write(data);
                write.close();
                out.close();
            }
            int responseCode = connection.getResponseCode();
            logger.log(Level.INFO, "responseCode:::::::::" + responseCode);
            InputStream content = null;

            if (responseCode == 201)
            {	// it is success
                content = (InputStream) connection.getInputStream();
            }
            else if (responseCode == 400)
            { // field is required
                content = (InputStream) connection.getErrorStream();
            }
	    else if(responseCode == 404)
	    { // field is required
		content = (InputStream)connection.getErrorStream();
	    }
            else if (responseCode == 401)
            { // unauthorized
                connection.disconnect();
                resultant = "{unauthorized:You are not authenticated. Authentication required to perform this operation}"; // No I18N
                return resultant;
            }
            else
            {
                content = (InputStream) connection.getInputStream();
            }
            if (content != null)
            {
                BufferedReader in = new BufferedReader(new InputStreamReader(content));
                String line;
                while ((line = in.readLine()) != null)
                {
                    resultant = line.toString();
                }
                in.close();
                connection.disconnect();
                return resultant;
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        return resultant;
    }

    private JSONObject constructReturnedJSONObject(String resultStr, HashMap completeDetailsMap)
    {
        JSONObject json = new JSONObject();
        JSONObject resultJson = new JSONObject();
        try
        {
            if (resultStr != null)
            {
                resultJson = new JSONObject(resultStr);
            }
        }
        catch (JSONException e)
        {
            e.printStackTrace();
        }
        try
        {
            HashMap successNFailureMap = (HashMap) completeDetailsMap.get("successNFailureMesgMap");
            if (resultJson.has("unauthorized"))
            {
                String unAuthMesg = (String) resultJson.get("unauthorized");
                json.put("result", "failure");
                json.put("message", unAuthMesg);

            }
            else if (resultJson.has("id"))
            {
                // Here we are Putting return jsonObject keys and values from jira  in a hashmap
                HashMap replaceMesgMap = new HashMap();
                Iterator i = resultJson.keys();
                while (i.hasNext())
                {
                    String key = i.next().toString();
                    String val = (String) resultJson.get(key);
                    replaceMesgMap.put(key, val);
                }

                String successMesg = (String) successNFailureMap.get("successMesg");
                json.put("result", "success");
                json.put("message", replaceMessage(successMesg, replaceMesgMap));

                JSONArray detailsArray = new JSONArray();

                ArrayList notesArr = (ArrayList) completeDetailsMap.get("notesArray");

                if (notesArr.size() > 0)
                {
                    JSONObject mainJson = new JSONObject();
                    mainJson.put("OPERATIONNAME", "ADD_NOTE");
                    JSONArray notesArray = new JSONArray();
                    for (int x = 0; x < notesArr.size(); x++)
                    {
                        String notes = (String) notesArr.get(x);
                        if (notes.contains("$"))
                        {
                            notes = replaceMessage(notes, replaceMesgMap);
                        }
                        JSONObject noteObject = new JSONObject();
                        JSONObject notesJson = new JSONObject();
                        noteObject.put("notestext", notes);
                        notesJson.put("notes", noteObject);
                        notesArray.put(notesJson);
                    }
                    mainJson.put("INPUT_DATA", notesArray);
                    detailsArray.put(mainJson);

                }
                //Updating a Request
                JSONArray updateReqArray = new JSONArray();
                JSONObject updateObj = new JSONObject();
                HashMap responseDetailsMap = (HashMap) completeDetailsMap.get("responseDetailsMap");
                Iterator iter = responseDetailsMap.keySet().iterator();
                while (iter.hasNext())
                {
                    String key = (String) iter.next();
                    String val = (String) responseDetailsMap.get(key);
                    if (val.contains("$"))
                    {
                        val = val.replace("$", "");
                        if (resultJson.has(val))
                        {
                            val = (String) resultJson.get(val);
                            updateObj.put(key, val);
                        }
                    }
                    else
                    {
                        updateObj.put(key, val);
                    }
                }
                updateReqArray.put(updateObj);
                JSONObject preJson3 = new JSONObject();
                preJson3.put("OPERATIONNAME", "EDIT_REQUEST");
                preJson3.put("INPUT_DATA", updateReqArray);
                detailsArray.put(preJson3);
                json.put("operation", detailsArray);
            }
            else if (resultJson.has("errors"))
            {
                String errorMessgs = resultJson.getString("errors");
                JSONObject errorTypeObj = new JSONObject(errorMessgs);
                Iterator i = errorTypeObj.keys();
                String failureMesg = "";
                while (i.hasNext())
                {
                    String key = i.next().toString();
                    String val = (String) errorTypeObj.get(key);
                    if ("".equals(failureMesg))
                    {
                        failureMesg = val;
                    }
                    else
                    {
                        failureMesg = failureMesg + " AND " + val; // No I18N
                    }
                }
                json.put("result", "failure");
                json.put("message", failureMesg);
            }
            else
            {
                json.put("result", "failure");
                String failureMesg = (String) successNFailureMap.get("failureMesg");
                json.put("message", failureMesg);
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return json;
    }

    /* This HashMap completeDetailsMap contains the information that read from XML File. This HashMap contains
     * 1.  authenticationDetails   HashMap : For storing the username,password and url
     *      {password=administrator, username=administrator, url=http://JiraServerName:port/rest/api/2/issue/}
     * 
     * 2. requestDetailsMap HashMap : For storing which filed in SCP to which field in jira Mapping
     *      {summary=$subject, issuetype=Bug, project=SCP, priority=$priority}
     *
     * 3. responseDetailsMap HashMap : For updating SCP Fields
     *      {JIRA_ISSUE_URL=$self, JIRA_ISSUE_ID=$id}
     *
     * 4. customFieldsTypeMap HashMap : For storing which type of field it is in jira
     *      {summary=textfield, issuetype=select, project=projectpicker, priority=select}
     *
     * 5. notesArray ArrayList  : For storing the noted to be added
     *      [Ticket is created in jira with key : $key  And with Id: $id, Ticket is created in jira with issueID : $id]
     *
     * 6. successNFailureMesgMap HashMap : For storing success and failure messages to be shown to the client
     *      {failureMesg=Failed to Integrate to jira, successMesg=Ticket is created in jira with key : $key  And with Id: $id}
     */
    private HashMap readXMLFile(String menuXMLName) throws Exception
    {
        HashMap completeDetailsMap = new HashMap();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setIgnoringElementContentWhitespace(true);
        factory.setIgnoringComments(true);
        DocumentBuilder builder = factory.newDocumentBuilder();

        String homeDir = SDDataManager.getInstance().getRootDir() + File.separator; // No i18n
        String confDir = homeDir + File.separator + "integration" + File.separator + "conf"; // No i18n
        String fileName = "Jira.xml";// NO I18N

        Document actionMenuDoc = builder.parse(new File(confDir, fileName));
        NodeList menuList = actionMenuDoc.getElementsByTagName(ActionExecutorConstant.MENU_ELEMENT);
        for (int i = 0; i < menuList.getLength(); i++)
        {
            Node menuNode = menuList.item(i);
            NamedNodeMap menuNodeAttr = menuNode.getAttributes();
            String menuName = menuNodeAttr.getNamedItem(ActionExecutorConstant.NAME_ATTRIBUTE).getNodeValue();
            if ((menuXMLName).equalsIgnoreCase(menuName))
            {
                HashMap authenticationDetails = new HashMap();
                HashMap requestDetailsMap = new HashMap();
                HashMap responseDetailsMap = new HashMap();
                HashMap customFieldsTypeMap = new HashMap();
                HashMap successNFailureMesgMap = new HashMap();
                ArrayList notesArr = new ArrayList();

                NodeList mainchildNodes = menuNode.getChildNodes();
                for (int k = 0; k < mainchildNodes.getLength(); k++)
                {
                    Node mainChildNode = mainchildNodes.item(k);
                    String mainNodeName = mainChildNode.getNodeName();
                    if (mainNodeName.equals("success"))
                    {
                        String successMesg = mainChildNode.getTextContent();
                        successNFailureMesgMap.put("successMesg", successMesg);
                    }
                    else if (mainNodeName.equals("failure"))
                    {
                        String failureMesg = mainChildNode.getTextContent();
                        successNFailureMesgMap.put("failureMesg", failureMesg);
                    }
                    else if (mainNodeName.equals("request"))
                    {
                        NodeList requestList = mainChildNode.getChildNodes();
                        for (int temp = 0; temp < requestList.getLength(); temp++)
                        {
                            Node reqNode = requestList.item(temp);
                            String reqNodeName = reqNode.getNodeName();
                            if (reqNodeName.equals("username"))
                            {
                                String userName = reqNode.getTextContent();
                                authenticationDetails.put("username", userName.trim());
                            }
                            else if (reqNodeName.equals("password"))
                            {
                                String passWord = reqNode.getTextContent();
                                authenticationDetails.put("password", passWord.trim());
                            }
                            else if (reqNodeName.equals("url"))
                            {
                                String url = reqNode.getTextContent();
                                authenticationDetails.put("url", url.trim());
                            }
                            else if (reqNodeName.equals("param"))
                            {
                                NodeList childParamNodes = reqNode.getChildNodes();
                                String key = null;
                                String value = null, type = null;
                                for (int w = 0; w < childParamNodes.getLength(); w++)
                                {
                                    Node childNode1 = childParamNodes.item(w);
                                    String nodeName1 = childNode1.getNodeName();
                                    String nodeValue1 = childNode1.getTextContent();
                                    if (nodeName1.equals("name"))
                                    {
                                        key = nodeValue1;
                                    }
                                    else if (nodeName1.equals("value"))
                                    {
                                        value = nodeValue1;
                                    }
                                    else if (nodeName1.equals("type"))
                                    {
                                        type = nodeValue1;
                                    }
                                }
                                requestDetailsMap.put(key.trim(), value.trim());
                                if (type != null)
                                {
                                    customFieldsTypeMap.put(key.trim(), type.trim());
                                }
                            }
                        }
                    }
                    else if (mainNodeName.equals("response"))
                    {
                        NodeList responseList = mainChildNode.getChildNodes();
                        for (int temp = 0; temp < responseList.getLength(); temp++)
                        {
                            Node respNode = responseList.item(temp);
                            String resNodeName = respNode.getNodeName();
                            if (resNodeName.equals("param"))
                            {
                                NodeList childParamNodes = respNode.getChildNodes();
                                String key = null;
                                String value = null;
                                for (int w = 0; w < childParamNodes.getLength(); w++)
                                {
                                    Node childNode1 = childParamNodes.item(w);
                                    String nodeName1 = childNode1.getNodeName();
                                    String nodeValue1 = childNode1.getTextContent();
                                    if (nodeName1.equals("name"))
                                    {
                                        key = nodeValue1;
                                    }
                                    else if (nodeName1.equals("value"))
                                    {
                                        value = nodeValue1;
                                    }
                                }
                                responseDetailsMap.put(key.trim(), value.trim());
                            }
                            else if (resNodeName.equals("notes"))
                            {
                                NodeList childParamNodes = respNode.getChildNodes();
                                for (int w = 0; w < childParamNodes.getLength(); w++)
                                {
                                    Node childNode2 = childParamNodes.item(w);
                                    String nodeName2 = childNode2.getNodeName();
                                    String nodeValue2 = childNode2.getTextContent();
                                    if (nodeName2.equals("note"))
                                    {
                                        notesArr.add(nodeValue2);
                                    }

                                }

                            }
                        }
                    }
                }
                completeDetailsMap.put("authenticationDetails", authenticationDetails);
                completeDetailsMap.put("requestDetailsMap", requestDetailsMap);
                completeDetailsMap.put("responseDetailsMap", responseDetailsMap);
                completeDetailsMap.put("customFieldsTypeMap", customFieldsTypeMap);
                completeDetailsMap.put("notesArray", notesArr);
                completeDetailsMap.put("successNFailureMesgMap", successNFailureMesgMap);
            }
        }
        logger.log(Level.INFO, "completeDetailsMap:::::::::" + completeDetailsMap);
        return completeDetailsMap;
    }

    private String replaceMessage(String message, HashMap replaceMesgMap) throws Exception
    {
        Iterator iter = replaceMesgMap.keySet().iterator();
        while (iter.hasNext())
        {
            String key = (String) iter.next();
            String val = (String) replaceMesgMap.get(key);
            message = message.replaceAll("\\$" + key, val);
        }
        return message;
    }

    private String replaceURL(String jiraURL,JSONObject scpValuesObj) throws Exception
	{
		String [] url = jiraURL.split("\\$");
		if(scpValuesObj.has(url[1])){
			String val = (String)scpValuesObj.get(url[1]);		
			jiraURL = jiraURL.replaceAll("\\$"+url[1],val);
		}		
		return jiraURL;
	}


	private JSONObject constructUpdateJiraJSONObject(String resultStr,HashMap completeDetailsMap)
	{
		JSONObject json = new JSONObject();
		JSONObject resultJson = new JSONObject();
		try {
			if(resultStr!=null){
				resultJson = new JSONObject(resultStr);
			}
		} catch (JSONException e) {
			e.printStackTrace();
		}

		logger.log(Level.INFO,"resultJson:::::::::"+ resultJson);
		try{
			HashMap successNFailureMap = (HashMap) completeDetailsMap.get("successNFailureMesgMap");
			if(resultJson.has("unauthorized")){
				String unAuthMesg = (String) resultJson.get("unauthorized");
				json.put("result","failure");
				json.put("message",unAuthMesg);

			}
			else if(resultJson.has("errorMessages")){
				JSONArray errorArray = resultJson.getJSONArray("errorMessages");
				String failureMesg = "";
				for (int i = 0; i < errorArray.length(); i++) {
					String message = (String) errorArray.getString(i);
					if("".equals(failureMesg)){
						failureMesg = message;
					}
					else{
						failureMesg = failureMesg + " AND "+message;// No I18N
					}
				}
				json.put("result","failure");
				json.put("message",failureMesg);
			}
			else if(resultJson.has("key") || resultJson.has("id")){
				String successMesg = (String) successNFailureMap.get("successMesg");
				json.put("result","success");	
				json.put("message",successMesg);
				JSONArray detailsArray = new JSONArray();
				JSONArray updateReqArray = new JSONArray();
				JSONObject updateObj = new JSONObject();
				HashMap responseDetailsMap = (HashMap)  completeDetailsMap.get("responseDetailsMap");
				Iterator iter = responseDetailsMap.keySet().iterator();
				while(iter.hasNext()) {
					String key = (String)iter.next();
					String val = (String)responseDetailsMap.get(key);
					if(val.contains("$")){
						String valArr[] = val.split(",");
						val = getAppropirtaeValue(valArr,resultStr);
						if(val!=null){
							updateObj.put(key,val);
						}			
					}
					else{
						updateObj.put(key,val);
					}		
				}
				updateReqArray.put(updateObj);
				JSONObject preJson3 = new JSONObject();
				preJson3.put("OPERATIONNAME","EDIT_REQUEST");
				preJson3.put("INPUT_DATA",updateReqArray);
				detailsArray.put(preJson3);
				json.put("operation", detailsArray);
			}
			else{
				json.put("result","failure");
				String failureMesg = (String) successNFailureMap.get("failureMesg");	
				json.put("message",failureMesg);
			}

		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		return json;
	}

	private String getAppropirtaeValue(String[] valArray, String  jiraResult ) 
	{
		String value = null;
		

		int arrLength = valArray.length;
		try{
			JSONObject jiraResultObj = new JSONObject(jiraResult);
			for(int i=0;i<arrLength; i++){
				String val = valArray[i];
				if(val.contains("$")){
					val = val.replace("$","");
					if(i<(arrLength-1)){
						try{
							jiraResultObj = (JSONObject) jiraResultObj.get(val);
						}

						catch(Exception e)
						{
							//e.printStackTrace();
							break;
						}
					}
					else{
						if(jiraResultObj.has(val)){
							value = (String) jiraResultObj.get(val);
						}

					}
				}

			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		return value;
	} 	
        public JSONObject getJiraConfigurations(Document jiraDoc,String query) throws Exception
        {
            Node node = CommonUtil.getInstance().findElement(jiraDoc, query);
            JSONObject jiraConf = new JSONObject();
            jiraConf = CommonUtil.getInstance().convertXmlNodeToJsonObject(node);
            return jiraConf;
        }
}
