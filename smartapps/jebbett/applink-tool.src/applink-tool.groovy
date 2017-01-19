/**
 *  appLink Tool - For Translating to non-appLink supporting apps
 *
 *  Copyright 2016 Jake Tebbett (jebbett)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 * 
 * VERSION CONTROL
 *
 *	V0.0.1	2016-11-23	Proposal
 *
 *	
 */
definition(
    name: "appLink Tool",
    namespace: "jebbett",
    author: "Jake Tebbett",
    description: "For appLink Management and translation to non-appLink apps",
    category: "My Apps",
	iconUrl: "https://raw.githubusercontent.com/jebbett/appLink-Tool/master/icons/appLink.png",
    iconX2Url: "https://raw.githubusercontent.com/jebbett/appLink-Tool/master/icons/appLink.png",
    iconX3Url: "https://raw.githubusercontent.com/jebbett/appLink-Tool/master/icons/appLink.png"
)

preferences {
	page name: "mainPage"
    page name: "runPage"
    page name: "delPage"
}

private mainPage() {
	dynamicPage(name: "mainPage", title: "", install: true, uninstall: true) {
		section ("Maintain & Test") {
            input "appList", "enum", title: "Published appLink Commands", required: false, submitOnChange: true, options: appLinkHandler(value: "list")
	        href(name: "runPage", title:"", description: "Test Selected Command", page: "runPage", state: "complete")
            href(name: "delPage", title:"", description: "Delete All Entries For Selected App From All appLink Apps", page: "delPage", state: "complete")
        }
        section ("Settings") {
            input "translateCore", "bool", title: "Translate CoRE Commands", defaultValue: false, submitOnChange: true, required: false
            if(translateCore){
            	paragraph("Translated Apps are prefixed with #") 
            }else{
            	sendLocationEvent(name: "appLink", value: "del" , isStateChange: true, descriptionText: "appLink Delete Event", data: [app: "#CoRE"])
            }
            input "testApp", "bool", title: "Create Dummy appLink [AppLinkTest]", defaultValue: false, submitOnChange: true, required: false
            if(testApp){
            	if(!atomicState.testEvent) atomicState.testEvent = "Latest Test Event: none"
                sendLocationEvent(name: "appLink", value: "add" , isStateChange: true, descriptionText: "appLink Add", data: ["$app.name":["Test Event":"Test Event"]])
                paragraph(atomicState.testEvent)
            }else{
            	sendLocationEvent(name: "appLink", value: "del" , isStateChange: true, descriptionText: "appLink Delete Event", data: [app: "$app.name"])
            }
        }    
	}
}

private runPage() {
	dynamicPage(name: "runPage", title: "appLink Request", install: true, uninstall: true) {
		section ("Request Sent") {
            appLinkHandler(value: "run", data: "$appList")
            href(name: "mainPage", title:"", description: "Back", page: "mainPage", state: "complete")
        }
	}
}

private delPage() {
	dynamicPage(name: "delPage", title: "appLink Delete", install: true, uninstall: true) {
		section ("Deleted AppLink App Reference") {
            sendLocationEvent(name: "appLink", value: "del" , isStateChange: true, descriptionText: "appLink Delete Event", data: [app: "${settings.appList.split(":")[0]}"])
            href(name: "mainPage", title:"", description: "Back", page: "mainPage", state: "complete")
		}
	}
}

def installed() {
    initialize()
}

def updated() {
    unsubscribe()
    initialize()
}

def initialize() {
    // Subscribe To New Activities To Trigger
    subscribe(location, "appLink", appLinkHandler)
    subscribe(location, "$app.name", appLinkTestHandler)
    // Subscribe to CoRE response
    if(translateCore){
    	subscribe(location, "CoRE", coreHandler)
        subscribe(location, "#CoRE", appLink3rdPtyHandler)
    }
}

def uninstalled() {
	sendLocationEvent(name: "appLink", value: "del" , isStateChange: true, descriptionText: "appLink Delete Event", data: [app: "#CoRE"])
	sendLocationEvent(name: "appLink", value: "del" , isStateChange: true, descriptionText: "appLink Delete Event", data: [app: "$app.name"])
}

/*****************************************************************/
/** appLink
/*****************************************************************/

// appLink core code: This should be included in every appLink compatible app, and not customised.
def appLinkHandler(evt){
    if(!state.appLink) state.appLink = [:]
    switch(evt.value) { //[appLink V0.0.2 2016-12-08]
   		case "add":	state.appLink << evt.jsonData;	break;
        case "del":	state.appLink.remove(evt.jsonData.app);	break;             
        case "list":	def list = [:];	state.appLink.each {key, value -> value.each{skey, svalue -> list << ["${key}:${skey}" : "[${key}] ${svalue}"]}};
        	return list.sort { a, b -> a.value.toLowerCase() <=> b.value.toLowerCase() };	break;
        case "run":	sendLocationEvent(name: "${evt.data.split(":")[0]}", value: evt.data.split(":")[1] , isStateChange: true, descriptionText: "appLink run"); break;
    }
    state.appLink.remove("$app.name") // removes this app from list
}

def appLink3rdPtyHandler(evt){
    switch(evt.name){
    	case "#CoRE": //Using Ask Alexa Interface
        	def data = [pistonName: evt.value, args: "I am activating the CoRE Macro: '${app.label}'."]
    		sendLocationEvent (name: "CoRE", value: "execute", data: data, isStateChange: true, descriptionText: "Barker triggered '${evt.value}' piston.")
        break;
    }
}

def appLinkTestHandler(evt){
	if(evt.value == "Test Event"){
    	state.testEvent = "Latest Test Event: ${new Date(now()).format("dd MMM HH:mm:ss", location.timeZone)}"
    }
}

/*****************************************************************/
/** CoRE
/*****************************************************************/

def getCoREMacroList(){ return getChildApps().findAll {it.macroType !="CoRE"}.label }

def coreHandler(evt) {
	log.debug "Refresh CoRE Piston List"
    def CoREPistons = []
    if (evt.value =="refresh" && translateCore) {
    	CoREPistons = evt.jsonData && evt.jsonData?.pistons ? evt.jsonData.pistons : []
        //Translate CoRE output in to AppLink
        def result = [:]
        CoREPistons.each { item -> result << ["$item" : item]}
        def list = [:]; list << ["#CoRE" : result]
        sendLocationEvent(name: "appLink", value: "add" , isStateChange: true, descriptionText: "appLink Add", data: list)
	}
}