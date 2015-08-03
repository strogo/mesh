package com.gentics.mesh.core.verticle.project;

import static com.gentics.mesh.core.data.relationship.Permission.CREATE_PERM;
import static com.gentics.mesh.core.data.relationship.Permission.READ_PERM;
import static com.gentics.mesh.core.data.relationship.Permission.UPDATE_PERM;
import static com.gentics.mesh.core.data.search.SearchQueue.SEARCH_QUEUE_ENTRY_ADDRESS;
import static com.gentics.mesh.json.JsonUtil.fromJson;
import static com.gentics.mesh.util.RoutingContextHelper.getPagingInfo;
import static com.gentics.mesh.util.RoutingContextHelper.getSelectedLanguageTags;
import static com.gentics.mesh.util.RoutingContextHelper.getUser;
import static com.gentics.mesh.util.VerticleHelper.delete;
import static com.gentics.mesh.util.VerticleHelper.hasSucceeded;
import static com.gentics.mesh.util.VerticleHelper.loadObject;
import static com.gentics.mesh.util.VerticleHelper.loadObjectByUuid;
import static com.gentics.mesh.util.VerticleHelper.loadTransformAndResponde;
import static com.gentics.mesh.util.VerticleHelper.transformAndResponde;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.vertx.core.http.HttpMethod.DELETE;
import static io.vertx.core.http.HttpMethod.GET;
import static io.vertx.core.http.HttpMethod.POST;
import static io.vertx.core.http.HttpMethod.PUT;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Route;

import org.apache.commons.lang3.StringUtils;
import org.jacpfx.vertx.spring.SpringVerticle;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.gentics.mesh.core.AbstractProjectRestVerticle;
import com.gentics.mesh.core.Page;
import com.gentics.mesh.core.data.Project;
import com.gentics.mesh.core.data.Tag;
import com.gentics.mesh.core.data.TagFamily;
import com.gentics.mesh.core.data.node.Node;
import com.gentics.mesh.core.data.search.SearchQueueEntryAction;
import com.gentics.mesh.core.rest.error.HttpStatusCodeErrorException;
import com.gentics.mesh.core.rest.node.NodeListResponse;
import com.gentics.mesh.core.rest.tag.TagCreateRequest;
import com.gentics.mesh.core.rest.tag.TagFamilyReference;
import com.gentics.mesh.core.rest.tag.TagListResponse;
import com.gentics.mesh.core.rest.tag.TagUpdateRequest;
import com.gentics.mesh.util.BlueprintTransaction;

/**
 * The tag verticle provides rest endpoints which allow manipulation and handling of tag related objects.
 * 
 * @author johannes2
 *
 */
@Component
@Scope("singleton")
@SpringVerticle
public class ProjectTagVerticle extends AbstractProjectRestVerticle {

	private static final Logger log = LoggerFactory.getLogger(ProjectTagVerticle.class);

	public ProjectTagVerticle() {
		super("tags");
	}

	@Override
	public void registerEndPoints() throws Exception {
		route("/*").handler(springConfiguration.authHandler());
		addCreateHandler();
		addReadHandler();
		addUpdateHandler();
		addDeleteHandler();

		addTaggedNodesHandler();
		if (log.isDebugEnabled()) {
			log.debug("Registered tag verticle endpoints");
		}
	}

	private void addTaggedNodesHandler() {
		Route getRoute = route("/:uuid/nodes").method(GET).produces(APPLICATION_JSON);
		getRoute.handler(rc -> {

			Project project = getProject(rc);
			loadObject(rc, "uuid", READ_PERM, project.getTagRoot(), rh -> {
				Tag tag = rh.result();
				Page<? extends Node> page = tag.findTaggedNodes(getUser(rc), getSelectedLanguageTags(rc), getPagingInfo(rc));
				transformAndResponde(rc, page, new NodeListResponse());
			});

		});
	}

	// TODO fetch project specific tag
	// TODO update other fields as well?
	// TODO Update user information
	private void addUpdateHandler() {
		Route route = route("/:uuid").method(PUT).consumes(APPLICATION_JSON).produces(APPLICATION_JSON);
		route.handler(rc -> {
			Project project = getProject(rc);
			loadObject(rc, "uuid", UPDATE_PERM, project.getTagRoot(), rh -> {
				if (hasSucceeded(rc, rh)) {
					Tag tag = rh.result();

					TagUpdateRequest requestModel = fromJson(rc, TagUpdateRequest.class);

					TagFamilyReference reference = requestModel.getTagFamilyReference();
					boolean updateTagFamily = false;
					if (reference != null) {
						// Check whether a uuid was specified and whether the tag family changed 
					if (!isEmpty(reference.getUuid())) {
						if (!tag.getTagFamily().getUuid().equals(reference.getUuid())) {
							updateTagFamily = true;
						}
					}
				}

				if (StringUtils.isEmpty(requestModel.getFields().getName())) {
					rc.fail(new HttpStatusCodeErrorException(BAD_REQUEST, i18n.get(rc, "tag_name_not_set")));
				} else {
					try (BlueprintTransaction tx = new BlueprintTransaction(fg)) {
						tag.setName(requestModel.getFields().getName());
						if (updateTagFamily) {
							/* TODO update the tagfamily */
						}
						searchQueue.put(tag.getUuid(), Tag.TYPE, SearchQueueEntryAction.UPDATE_ACTION);
						vertx.eventBus().send(SEARCH_QUEUE_ENTRY_ADDRESS, null);
						tx.success();
					}
					transformAndResponde(rc, tag);
				}
			}
		}	);
		});

	}

	// TODO load project specific root tag
	// TODO handle creator
	// TODO maybe projects should not be a set?
	private void addCreateHandler() {
		Route route = route("/").method(POST).consumes(APPLICATION_JSON).produces(APPLICATION_JSON);
		route.handler(rc -> {
			Project project = getProject(rc);
			Future<Tag> tagCreated = Future.future();
			TagCreateRequest requestModel = fromJson(rc, TagCreateRequest.class);

			if (StringUtils.isEmpty(requestModel.getFields().getName())) {
				rc.fail(new HttpStatusCodeErrorException(BAD_REQUEST, i18n.get(rc, "tag_name_not_set")));
			} else {
				TagFamilyReference reference = requestModel.getTagFamilyReference();
				if (reference == null || isEmpty(reference.getUuid())) {
					rc.fail(new HttpStatusCodeErrorException(BAD_REQUEST, i18n.get(rc, "tag_tagfamily_reference_not_set")));
				} else {
					loadObjectByUuid(rc, requestModel.getTagFamilyReference().getUuid(), CREATE_PERM, project.getTagFamilyRoot(), rh -> {
						if (hasSucceeded(rc, rh)) {
							try (BlueprintTransaction tx = new BlueprintTransaction(fg)) {
								TagFamily tagFamily = rh.result();
								Tag newTag = tagFamily.create(requestModel.getFields().getName(), project);
								getUser(rc).addCRUDPermissionOnRole(project.getTagFamilyRoot(), CREATE_PERM, newTag);
								project.getTagRoot().addTag(newTag);
								tagCreated.complete(newTag);
								searchQueue.put(newTag.getUuid(), Tag.TYPE, SearchQueueEntryAction.CREATE_ACTION);
								vertx.eventBus().send(SEARCH_QUEUE_ENTRY_ADDRESS, null);
								transformAndResponde(rc, newTag);
							}
						}
					});
				}
			}

		});
	}

	// TODO filtering, sorting
	private void addReadHandler() {
		Route route = route("/:uuid").method(GET).produces(APPLICATION_JSON);
		route.handler(rc -> {
			Project project = getProject(rc);
			loadTransformAndResponde(rc, "uuid", READ_PERM, project.getTagRoot());
		});

		Route readAllRoute = route().method(GET).produces(APPLICATION_JSON);
		readAllRoute.handler(rc -> {
			Project project = getProject(rc);
			loadTransformAndResponde(rc, project.getTagRoot(), new TagListResponse());
		});

	}

	// TODO filter by projectName
	private void addDeleteHandler() {
		Route route = route("/:uuid").method(DELETE).produces(APPLICATION_JSON);
		route.handler(rc -> {
			delete(rc, "uuid", "tag_deleted", getProject(rc).getTagRoot());
		});
	}

}
