package com.gentics.cailun.test;

import org.junit.runner.RunWith;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.gentics.cailun.core.data.service.LanguageService;
import com.gentics.cailun.core.data.service.RoleService;
import com.gentics.cailun.demo.DemoDataProvider;
import com.gentics.cailun.etc.CaiLunSpringConfiguration;

@ContextConfiguration(classes = { SpringTestConfiguration.class })
@RunWith(SpringJUnit4ClassRunner.class)
public abstract class AbstractDBTest {

	@Autowired
	private GraphDatabaseService graphDatabaseService;

	@Autowired
	protected LanguageService languageService;

	@Autowired
	private DemoDataProvider dataProvider;

	@Autowired
	protected RoleService roleService;

	@Autowired
	protected CaiLunSpringConfiguration springConfig;

	@Autowired
	protected GraphDatabaseService graphDb;

	public void setupData() {
		purgeDatabase();
		dataProvider.setup();
	}

	public DemoDataProvider data() {
		return dataProvider;
	}

	protected void purgeDatabase() {
		try (Transaction tx = graphDatabaseService.beginTx()) {
			for (Node node : graphDatabaseService.getAllNodes()) {
				for (Relationship rel : node.getRelationships()) {
					rel.delete();
				}
				node.delete();
			}
			tx.success();
		}
	}
}
