/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.messaging;

import java.util.Set;

import org.hornetq.core.security.Role;
import org.hornetq.core.server.HornetQServer;
import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;

/**
 * {@code OperationStepHandler} for adding a new security role.
 *
 * @author Emanuel Muckenhuber
 */
class SecurityRoleAdd implements OperationStepHandler {

    static final SecurityRoleAdd INSTANCE = new SecurityRoleAdd();

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        final Resource resource = context.createResource(PathAddress.EMPTY_ADDRESS);
        final ModelNode subModel = resource.getModel();
        for(final AttributeDefinition attribute : SecurityRoleDefinition.ATTRIBUTES) {
            attribute.validateAndSet(operation, subModel);
        }
        if(context.isNormalServer()) {
            context.addStep(new OperationStepHandler() {
                @Override
                public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
                    final PathAddress address = PathAddress.pathAddress(operation.require(ModelDescriptionConstants.OP_ADDR));
                    final HornetQServer server = getServer(context, operation);
                    if(server != null) {
                        final String match = address.getElement(address.size() - 2).getValue();
                        final String role = address.getLastElement().getValue();
                        final Set<Role> roles = server.getSecurityRepository().getMatch(match);
                        roles.add(SecurityRoleDefinition.transform(context, role, subModel));
                        server.getSecurityRepository().addMatch(match, roles);
                    }
                    // TODO handle rollback https://issues.jboss.org/browse/AS7-5690
                    context.completeStep(OperationContext.RollbackHandler.NOOP_ROLLBACK_HANDLER);
                }
            }, OperationContext.Stage.RUNTIME);
        }
        context.stepCompleted();
    }

    static HornetQServer getServer(final OperationContext context, ModelNode operation) {
        final ServiceName hqServiceName = MessagingServices.getHornetQServiceName(PathAddress.pathAddress(operation.get(ModelDescriptionConstants.OP_ADDR)));
        final ServiceController<?> controller = context.getServiceRegistry(true).getService(hqServiceName);
        if(controller != null) {
            return HornetQServer.class.cast(controller.getValue());
        }
        return null;
    }

}
