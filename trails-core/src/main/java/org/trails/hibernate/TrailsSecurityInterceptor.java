package org.trails.hibernate;

import java.io.Serializable;

import ognl.Ognl;
import ognl.OgnlException;

import org.acegisecurity.AuthenticationCredentialsNotFoundException;
import org.acegisecurity.GrantedAuthority;
import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;
import org.acegisecurity.userdetails.UserDetails;
import org.hibernate.CallbackException;
import org.hibernate.type.Type;
import org.trails.security.OwnerRequired;
import org.trails.security.RestrictionType;
import org.trails.security.RoleRequired;
import org.trails.security.TrailsSecurityException;
import org.trails.security.annotation.RemoveRequiresAssociation;
import org.trails.security.annotation.Restriction;
import org.trails.security.annotation.Security;
import org.trails.security.annotation.UpdateRequiresAssociation;
import org.trails.security.annotation.ViewRequiresAssociation;

public class TrailsSecurityInterceptor extends TrailsInterceptor {
	
	@Deprecated
	/*
	 * Deprecated in favor of the new security syntax in 1.1 
	 */
	private void checkRestriction(final Object entity, final RestrictionType restrictionType) {
		// Role-base restrictions override association based
		if (entity == null | restrictionType == null) return;
		
		boolean roleRestriction = false;
		Security security = entity.getClass().getAnnotation(Security.class);
		if (security != null) {
			Restriction[] restrictions = security.restrictions();
			for (Restriction restriction : restrictions) {
				if (restrictionType.equals(restriction.restrictionType()) ) {
					SecurityContext context = SecurityContextHolder.getContext();
					GrantedAuthority[] authorities = context.getAuthentication().getAuthorities();
					for (GrantedAuthority authority : authorities) if (restriction.requiredRole().equals(authority.getAuthority()) ) return;
					roleRestriction = true;
				}
			}
		}
		
		String ownerPropertyAssociation = null;
		switch (restrictionType) {
			case VIEW : 
				ViewRequiresAssociation viewRestriction = entity.getClass().getAnnotation(ViewRequiresAssociation.class );
				if (viewRestriction != null) ownerPropertyAssociation = viewRestriction.value();
			break;
			case UPDATE : 
				UpdateRequiresAssociation updateRestriction = entity.getClass().getAnnotation(UpdateRequiresAssociation.class );
				if (updateRestriction != null) ownerPropertyAssociation = updateRestriction.value();
			break;
			case REMOVE : 
				RemoveRequiresAssociation removeRestriction = entity.getClass().getAnnotation(RemoveRequiresAssociation.class );
				if (removeRestriction != null) ownerPropertyAssociation = removeRestriction.value();
			break;
		}
		// If check returns true, exit immediately, ignoring possible role restriction
		if (ownerPropertyAssociation != null) if (checkOwnershipRestriction(entity, ownerPropertyAssociation) ) return;
		
		// Note that because we place no restriction if no annotations are specified, but check the ownership restriction
		// only after role restriction, we need to delay throwing an exception if the role isn't available
		if (roleRestriction) throw new RoleRequired(entity, "Authenticated user does not have a required role or ownership");
		
	}
	
  private boolean checkOwnershipRestriction(final Object entity, final String associatedProperty) {
  	if (entity == null || associatedProperty == null) return false;

  	try {
			SecurityContext context = SecurityContextHolder.getContext();
			if (context.getAuthentication() == null) throw new AuthenticationCredentialsNotFoundException("Entity requires an authenticated user as owner");
			String currentUserName = context.getAuthentication().getName();
			if (currentUserName == null) currentUserName = "";
			
			// Empty string as associated property denotes entity itself
			if ("".equals(associatedProperty) ) {
				if (!(entity instanceof UserDetails) ) throw new TrailsSecurityException("Entity is not of type UserDetails"); 
				UserDetails userDetails = (UserDetails)entity;
				if (currentUserName.equals(userDetails.getUsername()) ) return true;
				else throw new OwnerRequired(entity, "Entity does not represent the authenticated user");
			}
			
			Object value = Ognl.getValue(associatedProperty, entity);
			if (value == null) throw new OwnerRequired(entity, "Associated owner property is null"); 
				
			if (value instanceof Iterable) {
				try {
					Iterable<UserDetails> iterable = (Iterable<UserDetails>)value;
					for (UserDetails userDetails : iterable) {
						if (currentUserName.equals(userDetails.getUsername()) ) {
							value = null;
							break;
						}
					}
				}
				catch (ClassCastException e) {
					throw new TrailsSecurityException("Associated collection doesn't contain UserDetails objects");
				}
				
				if (value != null) throw new OwnerRequired(entity, "Authenticated user is not in the owners collection");
			}
			else {
				if (!(value instanceof UserDetails) ) throw new TrailsSecurityException("Associate property is not of type UserDetails");
				UserDetails userDetails = (UserDetails)value;
				if (!currentUserName.equals(userDetails.getUsername()) ) throw new OwnerRequired(entity, "Authenticated user is not the owner");
			}
		}
		catch(OgnlException e) {
			throw new TrailsSecurityException("Could not evaluate the owner association", e);
		}
  	return false;
  }

  /* (non-Javadoc)
   * @see org.hibernate.Interceptor#onLoad(java.lang.Object, java.io.Serializable, java.lang.Object[], java.lang.String[], org.hibernate.type.Type[])
   */
  public boolean onLoad(Object entity, Serializable id, Object[] state,
      String[] propertyNames, Type[] types)
  {
		checkRestriction(entity, RestrictionType.VIEW);
		/*
		ViewRequiresAssociation ownerRestriction = entity.getClass().getAnnotation(ViewRequiresAssociation.class );
		if (ownerRestriction != null) checkOwnershipRestriction(entity, ownerRestriction.value());
		*/
		return super.onLoad(entity, id, state, propertyNames, types);
  }
  
  /* (non-Javadoc)
   * @see org.hibernate.Interceptor#onFlushDirty(java.lang.Object, java.io.Serializable, java.lang.Object[], java.lang.Object[], java.lang.String[], org.hibernate.type.Type[])
   */
  public boolean onFlushDirty(Object entity, Serializable id, Object[] currentState,
      Object[] previousState, String[] propertyNames, Type[] types) throws CallbackException
  {
		checkRestriction(entity, RestrictionType.UPDATE);
  	return super.onFlushDirty(entity, id, currentState, previousState, propertyNames, types);
  }

  /* (non-Javadoc)
   * @see org.hibernate.Interceptor#onSave(java.lang.Object, java.io.Serializable, java.lang.Object[], java.lang.String[], org.hibernate.type.Type[])
   */
  public boolean onSave(Object entity, Serializable id, Object[] state,
      String[] propertyNames, Type[] types) throws CallbackException
  {
		checkRestriction(entity, RestrictionType.UPDATE);
  	return super.onSave(entity, id, state, propertyNames, types);
  }

  /* (non-Javadoc)
   * @see org.hibernate.Interceptor#onDelete(java.lang.Object, java.io.Serializable, java.lang.Object[], java.lang.String[], org.hibernate.type.Type[])
   */
  public void onDelete(Object entity, Serializable arg1, Object[] arg2,
      String[] arg3, Type[] arg4) throws CallbackException
  {
		checkRestriction(entity, RestrictionType.REMOVE);
  	super.onDelete(entity, arg1, arg2, arg3, arg4);
  }
}
