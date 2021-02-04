package com.gentics.mesh.graphql.context;

import java.util.function.Supplier;

import com.gentics.mesh.context.InternalActionContext;
import com.gentics.mesh.core.data.HibCoreElement;
import com.gentics.mesh.core.data.HibNodeFieldContainer;
import com.gentics.mesh.core.data.node.NodeContent;
import com.gentics.mesh.core.data.perm.InternalPermission;
import com.gentics.mesh.core.rest.error.PermissionException;
import com.gentics.mesh.plugin.graphql.GraphQLPluginContext;

import graphql.schema.DataFetchingEnvironment;

/**
 * Extended context for GraphQL handling.
 */
public interface GraphQLContext extends InternalActionContext, GraphQLPluginContext {

	/**
	 * Check whether at least one of the provided permissions is granted. Otherwise a failure {@link PermissionException} will be thrown.
	 * 
	 * @param element
	 *            Element to be checked
	 * @param permission
	 * @return Provided element will be returned if at least one of the permissions grants access
	 * @throws PermissionException
	 */
	<T extends HibCoreElement> T requiresPerm(T element, InternalPermission... permission);

	/**
	 * Check whether the current user of the context has read permission on the container (via type and parent node).
	 * 
	 * @param container
	 * @return
	 */
	boolean hasReadPerm(HibNodeFieldContainer container);

	/**
	 * Check whether the current user of the context has read permission on the container (via type and parent node).
	 * This method will not fail with an exception. Instead the perm error will be logged in the error list. Null will be returned in this case.
	 * 
	 * @param container
	 * @param env Environment used to add perm errors
	 * @return Provided container or null if permissions are lacking.
	 */
	HibNodeFieldContainer requiresReadPermSoft(HibNodeFieldContainer container, DataFetchingEnvironment env);

	/**
	 * Check whether the content can be read by the current user. Please note that this method will not check READ perms on the node. It is only checking the content container of the node. 
	 * 
	 * @param content
	 * @return
	 */
	boolean hasReadPerm(NodeContent content);

	/**
	 * Gets a value from the context. If the value does not exist yet, the supplier will be called.
	 * The result is then stored in the context and also returned.
	 * @param key The key to be used for the value in the context
	 * @param supplier Generates a value if it does not exist
	 * @return The value saved in the context or generated by the supplier
	 */
	default <T> T getOrStore(String key, Supplier<T> supplier) {
		T value = get(key);
		if (value == null) {
			value = supplier.get();
			put(key, value);
		}
		return value;
	}
}
