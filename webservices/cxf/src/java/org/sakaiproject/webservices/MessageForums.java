/**
 * Copyright (c) 2005 The Apereo Foundation
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://opensource.org/licenses/ecl2
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sakaiproject.webservices;

import java.util.Iterator;
import java.util.List;

import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

import lombok.extern.slf4j.Slf4j;

import org.sakaiproject.api.app.messageforums.Area;
import org.sakaiproject.api.app.messageforums.DiscussionForum;
import org.sakaiproject.api.app.messageforums.DiscussionTopic;
import org.sakaiproject.api.app.messageforums.Message;
import org.sakaiproject.api.app.messageforums.Topic;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.ToolConfiguration;
import org.sakaiproject.tool.api.Session;

import org.sakaiproject.util.Xml;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

@WebService
@SOAPBinding(style = SOAPBinding.Style.RPC, use = SOAPBinding.Use.LITERAL)
@Slf4j
public class MessageForums extends AbstractWebService {

    /**
     * Key in the ThreadLocalManager for binding our current placement.
     */
    protected final static String CURRENT_PLACEMENT = "sakai:ToolComponent:current.placement";

    /**
     * Key in the ThreadLocalManager for binding our current tool.
     */
    protected final static String CURRENT_TOOL = "sakai:ToolComponent:current.tool";

    /**
     * Adds a message to an existing forum or if there are no forums to add, adds a forum
     * and then adds a message.
     *
     * @param sessionid the session to use
     * @param context   the context to use
     * @param forum     the forum title
     * @param user      the user id that wil be creating the forums / messages
     * @param title     the message title
     * @param body      the message body
     * @return the sessionid if active, or "null" if not.
     */
    @WebMethod
    @Path("/addMessage")
    @Produces("text/plain")
    @GET
    public String addMessage(
            @WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
            @WebParam(name = "context", partName = "context") @QueryParam("context") String context,
            @WebParam(name = "forum", partName = "forum") @QueryParam("forum") String forum,
            @WebParam(name = "topic", partName = "topic") @QueryParam("topic") String topic,
            @WebParam(name = "user", partName = "user") @QueryParam("user") String user,
            @WebParam(name = "title", partName = "title") @QueryParam("title") String title,
            @WebParam(name = "body", partName = "body") @QueryParam("body") String body) {
        Session s = establishSession(sessionid);


        // Wrap this in a big try / catch block so we get better feedback
        // in the logs in the case of an error
        try {
            Site site = siteService.getSite(context);

            ToolConfiguration tool = site.getToolForCommonId("sakai.forums");

            if (tool == null) {
                return "Tool sakai.forums not found in site=" + context;
            }

            // Lets go down and hack our essense into the thread
            threadLocalManager.set(CURRENT_PLACEMENT, tool);
            threadLocalManager.set(CURRENT_TOOL, tool.getTool());

            List<DiscussionForum> forums = messageForumsForumManager.getForumsForMainPage();

            Topic selectedTopic = null;
            Topic anyTopic = null;
            DiscussionForum selectedForum = null;
            DiscussionForum anyForum = null;
            DiscussionTopic dTopic = null;

            for (DiscussionForum dForum : forums) {
                anyForum = dForum;
                if (forum.equals(dForum.getTitle())) selectedForum = dForum;
                log.debug("forum = " + dForum + " ID=" + dForum.getId());
            }

            if (selectedForum == null) selectedForum = anyForum;
            if (selectedForum == null) {

                Area area = areaManager.getAreaByContextIdAndTypeId(context, messageForumsTypeManager.getDiscussionForumType());

                if (area == null) {
                    area = areaManager.createArea(messageForumsTypeManager.getDiscussionForumType(), context);
                    area.setName("AREA 51");
                    area.setEnabled(Boolean.TRUE);
                    area.setHidden(Boolean.TRUE);
                    area.setLocked(Boolean.FALSE);
                    area.setModerated(Boolean.FALSE);
                    area.setPostFirst(Boolean.FALSE);
                    area.setAutoMarkThreadsRead(false);
                    area.setSendEmailOut(Boolean.TRUE);
                    area.setAvailabilityRestricted(Boolean.FALSE);
                    area = areaManager.saveArea(area);
                    log.debug("Created area...");
                }

                selectedForum = messageForumsForumManager.createDiscussionForum();
                selectedForum.setArea(area);
                selectedForum.setCreatedBy(user);
                selectedForum.setTitle(forum);
                selectedForum.setDraft(false);
                selectedForum.setModerated(false);
                selectedForum.setPostFirst(false);
                selectedForum = messageForumsForumManager.saveDiscussionForum(selectedForum);
                log.debug("Created forum=" + forum);
                dTopic = messageForumsForumManager.createDiscussionForumTopic(selectedForum);
                dTopic.setTitle(topic);
                dTopic.setCreatedBy(user);
                messageForumsForumManager.saveDiscussionForumTopic(dTopic, false);
                log.debug("Created topic=" + topic);
                forums = messageForumsForumManager.getForumsForMainPage();
                selectedForum = null;
                for (DiscussionForum dForum : forums) {
                    anyForum = dForum;
                    if (forum.equals(dForum.getTitle())) selectedForum = dForum;
                    log.debug("forum = " + dForum + " ID=" + dForum.getId());
                }
            }

            if (selectedForum == null) selectedForum = anyForum;
            if (selectedForum == null) return "No forums found in site=" + context;

            for (Object o : selectedForum.getTopicsSet()) {
                dTopic = (DiscussionTopic) o;
                anyTopic = dTopic;
                if (topic.equals(dTopic.getTitle())) selectedTopic = dTopic;
                if (dTopic.getDraft().equals(Boolean.FALSE)) {
                    log.debug("Topic ID=" + dTopic.getId() + " title=" + dTopic.getTitle());
                }
            }

            if (selectedTopic == null) selectedTopic = anyTopic;
            if (selectedTopic == null) return "No topic";

            DiscussionTopic topicWithMsgs = (DiscussionTopic) discussionForumManager.getTopicByIdWithMessages(selectedTopic.getId());
            List tempList = topicWithMsgs.getMessages();
            Message replyMessage = null;
            if (tempList != null && tempList.size() > 0) {
                replyMessage = (Message) tempList.get(tempList.size() - 1);
            }

            Message aMsg;
            aMsg = messageForumsMessageManager.createDiscussionMessage();
            aMsg.setTitle(title);
            aMsg.setBody(body);
            aMsg.setAuthor(user);
            aMsg.setDraft(Boolean.FALSE);
            aMsg.setDeleted(Boolean.FALSE);
            aMsg.setApproved(Boolean.TRUE);
            aMsg.setTopic(selectedTopic);
            if (replyMessage != null) {
                aMsg.setInReplyTo(replyMessage);
            }
            discussionForumManager.saveMessage(aMsg);
            return "Success";
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }

        return "Failure";
    }

    /**
     * Lists all Forums for a site
     * @param sessionid the session to use
     * @param siteId    the siteId to use
     * @return the string representation of the forums within the site in XML
     */
    @WebMethod
    @Path("/getForumsInSite")
    @Produces("text/plain")
    @GET
    public String getForumsInSite(
            @WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
            @WebParam(name = "siteId", partName = "siteId") @QueryParam("siteId") String siteId) {
        Session session = establishSession(sessionid);

        try {
            Site site = siteService.getSite(siteId);
            if( site == null ) {
                log.warn("WS getForumsInSite(): Site not found.");
                throw new RuntimeException("WS getForumsInSite(): Site not found.");
            }

            // If not admin, check maintainer membership in the source site
    		if (!securityService.isSuperUser(session.getUserId()) && !securityService.unlock(siteService.SECURE_UPDATE_SITE, site.getReference()))
    		{
                log.warn("WS getForumsInSite(): Permission denied. Must be super user to getForumsInSite as part of a site in which you are not a maintainer.");
                throw new RuntimeException("WS getForumsInSite(): Permission denied. Must be super user to getForumsInSite as part of a site in which you are not a maintainer.");
            }

            ToolConfiguration tool = site.getToolForCommonId("sakai.forums");

            if (tool == null) {
                return "Tool sakai.forums not found in site=" + siteId;
            }

            // Lets go down and hack our essence into the thread
            threadLocalManager.set(CURRENT_PLACEMENT, tool);
            threadLocalManager.set(CURRENT_TOOL, tool.getTool());

            List<DiscussionForum> forums = messageForumsForumManager.getForumsForMainPage();

            Document dom = Xml.createDocument();
            Node list = dom.createElement("forumlist");
            dom.appendChild(list);

            int forumCnt = 0;
            if( forums != null ) {
                forumCnt = forums.size();

                for (DiscussionForum dForum : forums) {
                    Element forumNode = dom.createElement("forum");
                    list.appendChild(forumNode);

                    Attr idAttr = dom.createAttribute("id");
                    idAttr.setNodeValue(Long.toString(dForum.getId()));
                    forumNode.setAttributeNode(idAttr);

                    Node title = dom.createElement("title");
                    title.appendChild(dom.createTextNode(dForum.getTitle()));
                    forumNode.appendChild(title);

                    Element topiclist = dom.createElement("topiclist");
                    forumNode.appendChild(topiclist);

                    int topicCnt = 0;
                    if (dForum.getTopics() != null) {
                        for (Iterator itor = dForum.getTopics().iterator(); itor.hasNext(); ) {
                            topicCnt++;
                            DiscussionTopic topic = (DiscussionTopic) itor.next();

                            Element topicNode = dom.createElement("topic");
                            topiclist.appendChild(topicNode);

                            Attr topicidAttr = dom.createAttribute("id");
                            topicidAttr.setNodeValue(Long.toString(topic.getId()));
                            topicNode.setAttributeNode(topicidAttr);

                            Node topictitle = dom.createElement("title");
                            topictitle.appendChild(dom.createTextNode(topic.getTitle()));
                            topicNode.appendChild(topictitle);
                        }
                    }
                    Node topicTotal = dom.createElement("topiclistTotal");
                    topicTotal.appendChild(dom.createTextNode(Integer.toString(topicCnt)));
                    forumNode.appendChild(topicTotal);
                }
            }

            //add total size node (nice attribute to save the end user doing an XSLT count every time)
            Node forumTotal = dom.createElement("forumlistTotal");
            forumTotal.appendChild(dom.createTextNode(Integer.toString(forumCnt)));
            list.appendChild(forumTotal);

            return Xml.writeDocumentToString(dom);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><errormsg>permission-failed canEditSite</errormsg>";
    }

}
