import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.bc.issue.search.SearchService
import com.atlassian.jira.web.bean.PagerFilter
import org.apache.log4j.Level
import org.apache.log4j.Logger
import com.atlassian.mail.Email
import com.atlassian.mail.server.MailServerManager
import com.atlassian.mail.server.SMTPMailServer

def objectFacade = ComponentAccessor.getOSGiComponentInstanceOfType(ComponentAccessor.getPluginAccessor().getClassLoader().findClass("com.riadalabs.jira.plugins.insight.channel.external.api.facade.ObjectFacade"))
def searchService = ComponentAccessor.getComponent(SearchService.class)
def automationUser = ComponentAccessor.getUserManager().getUserByKey("automation")
def captainEmailAddress = objectFacade.loadObjectAttributeBean(object.getId(), 3495).attributeValue.getTextValue()
def log = Logger.getLogger("groovy-script-log")
log.setLevel(Level.DEBUG)

// get unresolved issues from jqlQuery
def jqlQuery = "project IN(\"AMS Helpdesk\", \"ICT Service Desk\") AND resolution = Unresolved AND \"Cost Center\" = " + object.getObjectKey()
def parseResult = searchService.parseQuery(automationUser, jqlQuery) 

// method to sendEmail, this gets triggered later on
def sendEmail (String address, String subject, String body) {
    SMTPMailServer mailServer = ComponentAccessor.getMailServerManager().getDefaultSMTPMailServer()
 
    if (mailServer) {
        Email email = new Email(address)
        email.setSubject(subject)
        email.setMimeType("text/html")
        email.setBody(body)
      
        ClassLoader threadClassLoader = Thread.currentThread().getContextClassLoader()
        Thread.currentThread().setContextClassLoader(SMTPMailServer.class.classLoader)
        mailServer.send(email)
        Thread.currentThread().setContextClassLoader(threadClassLoader)
    } else {
        log.error("Unable to retrieve mail server from JIRA configuration")
    }
}

// if result is valid, create email
if (parseResult.isValid()) {
    def searchResult = searchService.search(automationUser, parseResult.getQuery(), PagerFilter.getUnlimitedFilter())
    def issues = searchResult.getResults()
    def emailBody = """<html><body><div style="border-top: 15px solid #ba0c2f; padding-top: 10px;">&nbsp;</div><p>Dear,
    <br /> <br /> This email acts as a daily reminder of all unresolved requests related to your vessel.</p><br />"""

    // loop over issues and add to body
    issues.each { issue ->
        emailBody += """<table><tr><th>Ticket</th><td>""" + issue.getKey() + """</td></tr><tr><th>Title</th><td>""" + issue.getSummary() +
            """</td></tr><tr><th>Description</th><td>""" + issue.getDescription() + """</td></tr></table><br />"""
    }

    emailBody += """<br /> <p>Kind regards, <br /><br />The AMS Service Desk Team</p></div></body></html>"""

    // check if there are issue results from the query, if there are none there is no need to send an email
    if (issues) {
        // send email address
        def emailSubject = "Unresolved issues for ${object.getName()}"
        sendEmail (captainEmailAddress, emailSubject, emailBody)
    }
}
