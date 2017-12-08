/* $Id$ */
var jiraJsonObject={};
var sdpToJiraFieldsMappingJson={};
var isMandatoryFieldEmpty = false;

//Get all projects to list in dropdown
$(document).ready(function() {
    var data = {MENUID: MENUID, OPERATION: 'GetProjects'};//NO I18N
    var items = JSON.stringify(data);
    $.ajax({type: 'POST',url: '/servlet/ActionExecutorServlet',data: {data: items},//NO I18N
        success: function(response) {
            var resultJson=JSON.parse(response.replace(/\\/g,''));
            if(resultJson.failure){
                  $('.lodMsg').show().find('#resp_msg').text('Error: '+resultJson.failure+'!!');//NO I18N
                  $('#createTicket').removeAttr('disabled').css('background-color','#487db4');//NO I18N
            }
            else{
                addProjectOptions(resultJson.result);
            }
        }
    });
});

//add projects options to Project dropdown
function addProjectOptions(projects){
    var projectlength = projects.length;
    var htmlOptions=[];
    for (var i = 0; i < projectlength; i++){
        htmlOptions.push($(new Option(projects[i].name,projects[i].id)));
    }
    $('#projectID').append(htmlOptions);
    $('#projectID').change(function() {
        var selectedProjectId = $(this).val();//NO I18N
        $('#columnfields-2,#columnfields-1,#columnfields-3').empty();
        $('#issueID').find('option').not('[value=-1]').remove();//NO I18N
        if($(this).val()==='-1'){
            $('#issueID').parents('.adRdiv_noMargin').eq(0).addClass('hidden');//NO I18N
        }
        else{
            $('.lodMsg').hide();
            getProjectMetaData(selectedProjectId);
        }
    });   
}

//get selected project meta data to display
function getProjectMetaData(projectId){
    var data = {MENUID: MENUID, OPERATION: 'GetFields',PROJECTID:projectId};//NO I18N
    var items = JSON.stringify(data);
    $.ajax({type: 'POST',url: '/servlet/ActionExecutorServlet',data: {data: items},//NO I18N
        success: function(response) {
            var resultJson=JSON.parse(response.replace(/\\/g,''));
            if(resultJson.failure){
                  $('.lodMsg').show().find('#resp_msg').text('Error: '+resultJson.failure+'!!');//NO I18N
                  $('#createTicket').removeAttr('disabled').css('background-color','#487db4');//NO I18N
            }
            else{
                jiraJsonObject = resultJson.fields.projects[0];
                sdpToJiraFieldsMappingJson=resultJson.mappingFields;
                $('#issueID').parents('.adRdiv_noMargin').eq(0).removeClass('hidden');//NO I18N
                addIssueTypeOptions(jiraJsonObject.issuetypes);
            }
        }
    });    
}

//add issueType options to Issue Type dropdown based on selected project
function addIssueTypeOptions(issueTypes){
    var issueTypeLength = issueTypes.length;
    var htmlOptions='';
    for (var i = 0; i < issueTypeLength; i++){
        htmlOptions+=($(new Option(issueTypes[i].name,issueTypes[i].id)).attr('customId',i)[0]).outerHTML;//NO I18N
    }
    $('#issueID').append(htmlOptions);
    $('#issueID').change(function() {
        var selectedIssueId = $(this).find(':selected').attr('customId');//NO I18N
        var jiraFieldLabelToIdMap=addFieldsBasedOnProjectAndIssueType(selectedIssueId);
        setValuesOfSdpToJiraMappedFields(jiraFieldLabelToIdMap);
    });
}

//add fields based on selected project and issuetype
function addFieldsBasedOnProjectAndIssueType(selectedIssueId){

    var Fields = jiraJsonObject.issuetypes[selectedIssueId].fields;
    $('#columnfields-1,#columnfields-2,#columnfields-3').empty();
    var jiraCustomSchema='com.atlassian.jira.plugin.system.customfieldtypes:';//NO I18N
    var row_div, custom_row_div, fieldDiv;
    var jiraFieldLabelToIdMap={};
    var count = 0, customCount = 0;
    var input_div = $('#inputDiv').clone();
    var select_div = $('#selectDiv').clone();
    var textArea_div=$('#textAreaDiv').clone();
    var mandatory_Icon_Div=$('#mandatory').clone();
    var info_div=$('#infoDiv').clone();
    var rowdiv=$('#row_div').clone();
    $.each(Fields, function(i, item) {
        jiraFieldLabelToIdMap[Fields[i].name]=i;
        var schema=Fields[i].schema;
        var allowedValues=Fields[i].allowedValues;
        mandatory_Icon_Div.text('*');//NO I18N
          //ignore this fields
        if (!(schema.type === 'project' || schema.type === 'issuetype' || Fields[i].name === 'Attachment' || schema.system === 'worklog' || schema.type === 'datetime' || schema.type === 'user' || schema.system === 'parent' || schema.system === 'issuelinks' || schema.system==='resolution')){
            //Custom Cascade type fields
            if (schema.custom && schema.custom===jiraCustomSchema+'cascadingselect'){
                var casRowDiv =rowdiv.clone();
                var selectElement = select_div;
                //remove all options other than default
                selectElement.find('select option').not('[value=-1]').remove();//NO I18N
                selectElement.find('select').attr({'id': i,'style':'width:90%'}).removeAttr('multiselect');//NO I18N
                selectElement.find('span').text(Fields[i].name);
                var optionsLen = allowedValues.length, htmlOptions='';
                for (var k = 0; k < optionsLen; k++){
                    var title,value=allowedValues[k].id;
                    if (allowedValues[k].name){
                        title=allowedValues[k].name;
                    }
                    else{
                        title=allowedValues[k].value;
                    }
                    htmlOptions+=($(new Option(title,value)).attr('customId',k)[0]).outerHTML;//NO I18N
                }
                selectElement.find('select').append(htmlOptions);
                // make field as mandatory
                if (Fields[i].required === true)   
                {
                    selectElement.find('select').attr('mandatory','false');//NO I18N
                    mandatory_Icon_Div.appendTo(selectElement.find('.adLdiv'));
                }
                casRowDiv.append(selectElement.html());
                casRowDiv.removeAttr('id');//NO I18N
                selectElement = select_div.clone();
                selectElement.find('select option').not('[value=-1]').remove();//NO I18N
                selectElement.find('select').attr('style','width:90%');//NO I18N
                casRowDiv.append(selectElement.find('.adFdiv'));
                $('#columnfields-1').append(casRowDiv);
                
                $('#' + i).change(function(e) {
                    var childSelectElementId=i+$(this).find(':selected').val();
                    selectElement = select_div;
                    selectElement.find('select option').not('[value=-1]').remove();//NO I18N
                    selectElement.find('select').attr({id: childSelectElementId,style:'width:90%'}).removeAttr('multiple');//NO I18N
                    selectElement.find('.adLdiv').remove();
                    var selectedOptionId = $(this).find(':selected').attr('customId');//NO I18N
                    var selectOptionId = parseInt(selectedOptionId);
                    if(allowedValues[selectOptionId]){
                        var options=allowedValues[selectOptionId].children, optionsLen = options.length;
                        var htmlOptions='';
                        for (var k = 0; k < optionsLen; k++){
                            var title,value=options[k].id;
                            if (options[k].name){
                                title=options[k].name;
                            }
                            else{
                                title=options[k].value;
                            }
                            htmlOptions+=($(new Option(title,value))[0]).outerHTML;
                        }
                        selectElement.find('select').append(htmlOptions);
                    }
                    $('#'+i).parent().nextAll().remove();
                    $('#'+i).parent().parent().append(selectElement.html());
                });
            }
            else{  
                 //default fields and to align two fields in one row
                if (!schema.custom&& count % 2 === 0){
                    row_div = rowdiv.clone();
                    row_div.removeAttr('id').appendTo($('#columnfields-2'));//NO I18N
                } 
                //custom  fields and to align two fields in one row
                else if (schema.custom&& customCount % 2 === 0){
                    custom_row_div =rowdiv.clone();
                    custom_row_div.removeAttr('id').appendTo($('#columnfields-3'));//NO I18N
                }
                
                if(allowedValues){// select type field
                    fieldDiv= select_div;
                    fieldDiv.find('span').text(Fields[i].name).end().find('select option').not('[value=-1]').remove();//NO I18N
                    fieldDiv.find('#mandatory').remove();
                    
                    if (schema.type === 'array'){//multiple select type
                        fieldDiv.find('select').attr({multiple: 'multiple', id: i,style:'width:50%'});//NO I18N
                    }
                    else{
                        fieldDiv.find('select').attr({id: i,style:'width:50%'}).removeAttr('multiple');//NO I18N
                    }
                    //adding options to select dropdown
                    var optionsLen = allowedValues.length, htmlOptions='';
                    for (var k = 0; k < optionsLen; k++){
                        var title,value=allowedValues[k].id;
                        if (allowedValues[k].name){
                            title=allowedValues[k].name;
                        }
                        else{
                            title=allowedValues[k].value;
                        }
                        htmlOptions+=($(new Option(title,value))[0]).outerHTML;
                    }
                    fieldDiv.find('select').append(htmlOptions);
                    if (Fields[i].required) {// add  mandatory fields icon
                        mandatory_Icon_Div.appendTo(fieldDiv.find('.adLdiv'));
                        fieldDiv.find('select').attr('mandatory','true');//NO I18N
                    }
                    else{
                        fieldDiv.find('select').attr('mandatory','false');//NO I18N
                    }
                }
                else if(schema.system === 'description'){
                    fieldDiv=textArea_div;
                    fieldDiv.find('span').text(Fields[i].name).end().find('textarea').attr('id', i).end().find('#mandatory').remove();
                    if (Fields[i].required === true) {// add  mandatory fields icon
                        mandatory_Icon_Div.appendTo(fieldDiv.find('.adLdiv')); 
                        fieldDiv.find('textarea').attr('mandatory','true');//NO I18N
                    }
                    else
                    {
                        fieldDiv.find('textarea').attr('mandatory','false');//NO I18N
                    }
                }
                else{
                    fieldDiv=input_div;
                    fieldDiv.find('span').text(Fields[i].name).end().find('input').attr('id', i).end().find('#mandatory').remove();
                    if (Fields[i].required === true){// add  mandatory fields icon
                        mandatory_Icon_Div.appendTo(fieldDiv.find('.adLdiv'));
                        fieldDiv.find('input').attr('mandatory','true');//NO I18N
                    }
                    else{
                        fieldDiv.find('input').attr('mandatory','false');//NO I18N
                    }
                }
                fieldDiv.find('.fieldInfo').remove();
                //add information message  for date type fields
                if (schema.type === 'date') {
                    info_div.removeAttr('id').text('Enter as: 2014-12-30').appendTo(fieldDiv.find('.adFdiv'));//NO I18N
                }
                //add information message for date type fields
                else if (schema.type === 'timetracking') {
                    info_div.removeAttr('id').text('Enter as: 5w 6d 4h,3w 2d 23h').appendTo(fieldDiv.find('.adFdiv'));//NO I18N
                }
                //add information message for array  fields
                else if (schema.type ==='array' && !allowedValues){
                    info_div.removeAttr('id').text('Enter as: tag1, tag2, tag3...').appendTo(fieldDiv.find('.adFdiv'));//NO I18N
                }
                //add information for url fields
                else if(schema.custom&&schema.custom===jiraCustomSchema+'url'){
                    info_div.removeAttr('id').text('Enter as: http://www.abc.com').appendTo(fieldDiv.find('.adFdiv'));//NO I18N
                }
                if (!schema.custom) {
                    row_div.append(fieldDiv.html());
                    count++;
                } else {
                    custom_row_div.append(fieldDiv.html());
                    customCount++;
                }
            }
        }
    });
    return jiraFieldLabelToIdMap;
}

//set value of fields based on sdp to jira mapped fields
function setValuesOfSdpToJiraMappedFields(jiraFieldLabelToIdMap){
    
    for(var i=0,len=sdpToJiraFieldsMappingJson.length;i<len;i++){
        var jiraFieldLabel=sdpToJiraFieldsMappingJson[i].name;
        var jiraFieldId=jiraFieldLabelToIdMap[jiraFieldLabel];
        var sdpField=sdpToJiraFieldsMappingJson[i].value;
        var type=sdpToJiraFieldsMappingJson[i].type;
        if(type==='TEXT'||type==='TEXTAREA'){
            $('#'+jiraFieldId).val(sdpJson[sdpField]);
        }
        else if(type==='DATE'){            
            var sdpDateValue=parseInt(sdpJson[sdpField]);
            if(sdpDateValue!==-1){
                var sdpDate=new Date(sdpDateValue); 
                var jiraDate='';
                jiraDate+=sdpDate.getFullYear();
                jiraDate+='-'+sdpDate.getMonth();
                jiraDate+='-'+sdpDate.getDate();
                $('#'+jiraFieldId).val(jiraDate);
            }
        }
        else if(type==='NUMERIC'){
            var sdpValue=parseInt(sdpJson[sdpField]);
            $('#'+jiraFieldId).val(sdpValue);
        }
    }
}

//construct JSONObject of field values to call JIRA APIs
function getFieldsValue(selectedIssueId){     

    var fieldValues={};
    var ids = '-1', value = '',  noValueExist;//NO I18N
    var allFields = jiraJsonObject.issuetypes[selectedIssueId].fields;
    var jiraCustomSchema='com.atlassian.jira.plugin.system.customfieldtypes:';//NO I18N
    $.each(allFields, function(fieldId, item) {
        if(isMandatoryFieldEmpty){
            return;
        }
        ids = '0', value = '', noValueExist=false;
        var valuesArray = [], fieldArray = [];
        var field=allFields[fieldId], schemaType = field.schema.type, allowedValues = field.allowedValues, customField=field.schema.custom;
        var fieldObject=$('#' + fieldId);
        if(fieldObject.length){
            //remove error or warnings if already present made during previous request
            fieldObject.parent().find('#errors'+fieldId).remove();  
            var isMandatoryField=(fieldObject.attr('mandatory')==='true');
            //Custom Casecade/dependent fields
            if ( customField && customField===jiraCustomSchema+'cascadingselect'){
                ids = fieldObject.val();
                if (ids !== '-1') {
                    var arr ={id:ids};
                    var childid = $('#'+fieldId +ids).val();
                    if (childid !== '-1'){    
                        arr.child={id: childid};
                    }
                    fieldValues[fieldId] = arr;
                }
                else{
                    noValueExist=true;
                }
            }
            //multiple option select field
            else if (allowedValues && schemaType === 'array'){
                valuesArray = fieldObject.val();
                if (valuesArray) {
                    var valuesArrayLen=valuesArray.length;
                    for (var p = 0; p < valuesArrayLen; p++){
                        if (valuesArray[p] !== '-1'){
                            fieldArray.push({'id': valuesArray[p]});
                        }
                    }
                }
                if (fieldArray.length){
                    fieldValues[fieldId] = fieldArray;
                }
                else{
                    noValueExist=true;
                }
            }
            //single option select field
            else if (allowedValues){
                ids = fieldObject.find(':selected').val();
                if (ids !== '-1'){
                    fieldValues[fieldId] = {'id': ids};
                }
                else{
                    noValueExist=true;
                }
            }
            //multiple values comma separated ie array of values
            else if (schemaType === 'array') {
                value = fieldObject.val();
                if (value && value !== ''){
                    valuesArray = value.split(',');
                    fieldValues[fieldId] = valuesArray;
                }
                else{
                    noValueExist=true;
                }
            }
            //input type fields
            else{
                value = fieldObject.val();
                if (value&& value !== '') {
                    //convert string to number
                    if (schemaType === 'number'){
                        fieldValues[fieldId] = parseInt(value);
                    }
                    //time tracking fields
                    else if (schemaType === 'timetracking'){
                        valuesArray = value.split(',');
                        fieldValues[fieldId] = {originalEstimate: valuesArray[0], remainingEstimate: valuesArray[1]};
                    }
                    else{
                        fieldValues[fieldId] = value;
                    }
                }
                else{
                    noValueExist=true;
                }
            }
            if(isMandatoryField&&noValueExist){
                listError(fieldId);
                isMandatoryFieldEmpty = true;
            }
        }
    });
    return fieldValues;
}

//add error to fields
function listError(fieldId){
    var fieldObject=$('#' + fieldId);
    fieldObject.parent().find('#errors'+fieldId).remove();
    var errorDIV = $('#errorDiv').clone();
    errorDIV.attr('id','errors'+fieldId).appendTo(fieldObject.parent());
}

//called on submit issue, first validate fields and constructs data to send and than call the server with required details.
function createJiraTicket(){

    $('.lodMsg').hide();
    var issueTypeId=$('#issueID').find(':selected').attr('customId');//NO I18N
    var fieldValues = getFieldsValue(issueTypeId);
    if (isMandatoryFieldEmpty)
    {
        isMandatoryFieldEmpty =false;
        return;
    }    
    fieldValues.project = {id:$('#projectID').val()};
    fieldValues.issuetype = {id:$('#issueID').val()};
    var returnJson={fields:fieldValues};
    var Jdata = JSON.stringify(returnJson);
    var data = {MENUID: MENUID, OPERATION: 'SaveTicket',TicketData:Jdata};//NO I18N
    var items = JSON.stringify(data);
    $('.lodMsg').show().find('#resp_msg').text('Loading ...');//NO I18N
    window.scrollTo(0,0);
    $.ajax({type: 'POST',url: '/servlet/ActionExecutorServlet',data: {data: items},async: false,//NO I18N
        success: function(response) {
            var resultJson=JSON.parse(response.replace(/\\/g,'')).result;
            $('.lodMsg').hide();
            if (resultJson.errors){
                var errorDiv = $('#errorDiv').clone();
                $.each(resultJson.errors, function(i, message) {
                    $('#' + i).parent().find('#errors'+i).remove();
                    errorDiv.text(message).attr('id', 'errors' + i);
                    errorDiv.appendTo($('#' + i).parent());
                });
            }
            else{
                $('[id^=errors]').remove();
                var resultstring='Ticket created with Id: '+resultJson.id+' and Key: '+resultJson.key;//NO I18N
                $('.lodMsg').show().find('#resp_msg').text(resultstring);
                $('#createTicket').removeAttr('disabled').css('background-color','#487db4');//NO I18N
                $('#projectID').val("-1").trigger('change');//NO I18N
            }
        }
    });
}