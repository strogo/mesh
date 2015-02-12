package com.gentics.cailun.core.verticle;

import static io.vertx.core.http.HttpMethod.GET;
import io.vertx.ext.graph.neo4j.Neo4jGraphVerticle;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import org.jacpfx.vertx.spring.SpringVerticle;
import org.neo4j.graphdb.DynamicLabel;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gentics.cailun.core.AbstractCaiLunProjectRestVerticle;
import com.gentics.cailun.core.link.CaiLunLinkResolver;
import com.gentics.cailun.core.link.CaiLunLinkResolverFactoryImpl;
import com.gentics.cailun.core.link.LinkReplacer;
import com.gentics.cailun.core.repository.GlobalContentRepository;
import com.gentics.cailun.core.repository.GlobalTagRepository;
import com.gentics.cailun.core.rest.model.Content;
import com.gentics.cailun.core.rest.model.Tag;
import com.gentics.cailun.core.rest.response.GenericResponse;
import com.google.common.collect.Lists;

@Component
@Scope("singleton")
@SpringVerticle
public class QueryVerticle extends AbstractCaiLunProjectRestVerticle {

	@Autowired
	private GlobalContentRepository contentRepository;

	@Autowired
	private GlobalTagRepository tagRepository;

	@Autowired
	private CaiLunLinkResolverFactoryImpl<CaiLunLinkResolver> resolver;

	public QueryVerticle() {
		super("query");
	}

	@Override
	public void registerEndPoints() throws Exception {
		addGetPathHandler();
	}

	private void resolveLinks(Content content) throws InterruptedException, ExecutionException {
		// TODO fix issues with generics - Maybe move the link replacer to a spring service
		LinkReplacer replacer = new LinkReplacer(resolver);
		content.setContent(replacer.replace(content.getContent()));
	}

	private Long getPageNodeIdForPath(String path) throws Exception {
		String parts[] = path.split("/");
		Tag rootTag = tagRepository.findRootTag();
		GraphDatabaseService graphDb = Neo4jGraphVerticle.getDatabase();
		try (Transaction tx = graphDb.beginTx()) {
			Node currentNode = graphDb.getNodeById(rootTag.getId());
			for (int i = 0; i < parts.length - 1; i++) {
				String part = parts[i];
				Node nextNode = getChildNodeTagFromNodeTag(currentNode, part);
				if (nextNode != null) {
					currentNode = nextNode;
				} else {
					currentNode = null;
					break;
				}
			}
			if (currentNode != null) {
				// Finally search for the page and assume the last part of the request as filename
				Node pageNode = getChildNodePageFromNodeTag(currentNode, parts[parts.length - 1]);
				if (pageNode != null) {
					return pageNode.getId();
				} else {
					return null;
				}
			}
		}
		return null;
	}

	private Node getChildNodePageFromNodeTag(Node node, String pageFilename) {
		AtomicReference<Node> foundNode = new AtomicReference<>();
		Lists.newArrayList(node.getRelationships()).stream().filter(rel -> "TAGGED".equalsIgnoreCase(rel.getType().name())).forEach(rel -> {
			Node nextHop = rel.getStartNode();
			if (nextHop.hasLabel(DynamicLabel.label("Page"))) {
				String currentName = (String) nextHop.getProperty("filename");
				if (pageFilename.equalsIgnoreCase(currentName)) {
					foundNode.set(nextHop);
					return;
				}
			}
		});
		return foundNode.get();

	}

	private Node getChildNodeTagFromNodeTag(Node node, String tagName) {
		AtomicReference<Node> foundNode = new AtomicReference<>();
		Lists.newArrayList(node.getRelationships()).stream().filter(rel -> "TAGGED".equalsIgnoreCase(rel.getType().name())).forEach(rel -> {
			Node nextHop = rel.getEndNode();
			if (nextHop.hasLabel(DynamicLabel.label("Tag"))) {
				String currentName = (String) nextHop.getProperty("name");
				if (tagName.equalsIgnoreCase(currentName)) {
					foundNode.set(nextHop);
					return;
				}
			}
		});

		return foundNode.get();
	}

	private void addGetPathHandler() {
		getRouter().routeWithRegex("\\/get\\/(.*)").method(GET).handler(rc -> {
			try {
				String path = rc.request().params().get("param0");
				// TODO check whether pageRepository.findAllByTraversal(startNode, traversalDescription) might be an alternative
				Long pageId = getPageNodeIdForPath(path);
				if (pageId != null) {
					Content content = contentRepository.findOne(pageId);
					resolveLinks(content);
					ObjectMapper mapper = new ObjectMapper();
					String json = mapper.writeValueAsString(new GenericResponse<Content>(content));
					rc.response().end(json);
				} else {
					rc.fail(new Exception("Page for path {" + path + "} could not be found."));
					// TODO add json response - Make error responses generic
					rc.response().end("Element not found");
				}
			} catch (Exception e) {
				e.printStackTrace();
			}

		});

	}

}
