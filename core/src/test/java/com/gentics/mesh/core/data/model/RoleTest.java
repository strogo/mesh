package com.gentics.mesh.core.data.model;

import static com.gentics.mesh.core.data.model.relationship.Permission.CREATE_PERM;
import static com.gentics.mesh.core.data.model.relationship.Permission.DELETE_PERM;
import static com.gentics.mesh.core.data.model.relationship.Permission.READ_PERM;
import static com.gentics.mesh.core.data.model.relationship.Permission.UPDATE_PERM;
import static com.gentics.mesh.util.RoutingContextHelper.getUser;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import io.vertx.ext.web.RoutingContext;

import java.util.Set;

import org.junit.Before;
import org.junit.Test;

import com.gentics.mesh.core.Page;
import com.gentics.mesh.core.data.model.relationship.Permission;
import com.gentics.mesh.core.data.model.tinkerpop.MeshNode;
import com.gentics.mesh.core.data.model.tinkerpop.MeshShiroUser;
import com.gentics.mesh.core.data.model.tinkerpop.MeshUser;
import com.gentics.mesh.core.data.model.tinkerpop.Role;
import com.gentics.mesh.demo.UserInfo;
import com.gentics.mesh.paging.PagingInfo;
import com.gentics.mesh.test.AbstractDBTest;
import com.gentics.mesh.util.InvalidArgumentException;

public class RoleTest extends AbstractDBTest {

	private UserInfo info;

	@Before
	public void setup() throws Exception {
		setupData();
		info = data().getUserInfo();
	}

	@Test
	public void testCreation() {
		final String roleName = "test";
		Role role = roleService.create(roleName);
		role = roleService.findOne(role.getId());
		assertNotNull(role);
		assertEquals(roleName, role.getName());
	}

	@Test
	public void testGrantPermission() {
		Role role = info.getRole();
		MeshNode node = data().getContent("news overview");
		MeshNode node2;
		role.addPermissions(node, CREATE_PERM, READ_PERM, UPDATE_PERM, DELETE_PERM);

		// node2
		node2 = nodeService.create();
		node2.setContent(data().getEnglish(), "Test");
		role.addPermissions(node2, READ_PERM, DELETE_PERM);
		role.addPermissions(node2, CREATE_PERM);
		Set<Permission> permissions = role.getPermissions(node2);

		assertNotNull(permissions);
		assertTrue(permissions.contains(CREATE_PERM));
		assertTrue(permissions.contains(READ_PERM));
		assertTrue(permissions.contains(DELETE_PERM));
		assertFalse(permissions.contains(UPDATE_PERM));
		role.addPermissions(role, CREATE_PERM);
	}

	@Test
	public void testIsPermitted() throws Exception {
		MeshUser user = info.getUser();
		long start = System.currentTimeMillis();
		int nRuns = 200000;
		for (int i = 0; i < nRuns; i++) {
			user.hasPermission(data().getFolder("news"), READ_PERM);
		}
		long dur = System.currentTimeMillis() - start;
		System.out.println("Duration: " + dur / (double) nRuns);
	}

	@Test
	public void testGrantPermissionTwice() {
		Role role = info.getRole();
		MeshNode node = data().getContent("news overview");

		role.addPermissions(node, CREATE_PERM);
		role.addPermissions(node, CREATE_PERM);

		Set<Permission> permissions = role.getPermissions(node);
		assertNotNull(permissions);
		assertTrue(permissions.contains(CREATE_PERM));
		assertTrue(permissions.contains(READ_PERM));
		assertTrue(permissions.contains(DELETE_PERM));
		assertTrue(permissions.contains(UPDATE_PERM));
	}

	@Test
	public void testGetPermissions() {
		Role role = info.getRole();
		MeshNode node = data().getContent("news overview");
		assertEquals(4, role.getPermissions(node).size());
	}

	@Test
	public void testRevokePermission() {
		Role role = info.getRole();
		MeshNode node = data().getContent("news overview");
		role.revokePermissions(node, CREATE_PERM);

		Set<Permission> permissions = role.getPermissions(node);
		assertNotNull(permissions);
		assertFalse(permissions.contains(CREATE_PERM));
		assertTrue(permissions.contains(DELETE_PERM));
		assertTrue(permissions.contains(UPDATE_PERM));
		assertTrue(permissions.contains(READ_PERM));
	}

	@Test
	public void testRevokePermissionOnGroupRoot() throws Exception {

		info.getRole().revokePermissions(data().getMeshRoot().getGroupRoot(), CREATE_PERM);
		MeshUser user = info.getUser();
		assertFalse("The create permission to the groups root node should have been revoked.", user.hasPermission(data().getMeshRoot(), CREATE_PERM));

	}

	@Test
	public void testRoleRoot() {
		int nRolesBefore = roleService.findRoot().getRoles().size();

		final String roleName = "test2";
		Role role = roleService.create(roleName);
		assertNotNull(role);
		int nRolesAfter = roleService.findRoot().getRoles().size();
		assertEquals(nRolesBefore + 1, nRolesAfter);

	}

	@Test
	public void testRolesOfGroup() throws InvalidArgumentException {

		Role extraRole = roleService.create("extraRole");
		info.getGroup().addRole(extraRole);
		info.getRole().addPermissions(extraRole, READ_PERM);

		RoutingContext rc = getMockedRoutingContext("");
		MeshShiroUser requestUser = getUser(rc);
		Page<? extends Role> roles = info.getGroup().getRoles(requestUser, new PagingInfo(1, 10));
		assertEquals(2, roles.getSize());
		//assertEquals(2, roles.getTotalElements());
	}
}
