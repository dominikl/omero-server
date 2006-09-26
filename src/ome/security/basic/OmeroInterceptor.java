/*
 * ome.security.basic.OmeroInterceptor
 *
 *------------------------------------------------------------------------------
 *
 *  Copyright (C) 2005 Open Microscopy Environment
 *      Massachusetts Institute of Technology,
 *      National Institutes of Health,
 *      University of Dundee
 *
 *
 *
 *    This library is free software; you can redistribute it and/or
 *    modify it under the terms of the GNU Lesser General Public
 *    License as published by the Free Software Foundation; either
 *    version 2.1 of the License, or (at your option) any later version.
 *
 *    This library is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *    Lesser General Public License for more details.
 *
 *    You should have received a copy of the GNU Lesser General Public
 *    License along with this library; if not, write to the Free Software
 *    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 *------------------------------------------------------------------------------
 */

package ome.security.basic;

//Java imports
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.Iterator;

//Third-party libraries
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.CallbackException;
import org.hibernate.EmptyInterceptor;
import org.hibernate.EntityMode;
import org.hibernate.Interceptor;
import org.hibernate.Transaction;
import org.hibernate.type.Type;
import org.springframework.util.Assert;

//Application-internal dependencies
import ome.annotations.RevisionDate;
import ome.annotations.RevisionNumber;
import ome.conditions.InternalException;
import ome.model.IObject;
import ome.model.internal.Details;
import ome.model.internal.Permissions.Flag;
import ome.tools.hibernate.HibernateUtils;

/** 
 * implements {@link org.hibernate.Interceptor} for controlling various
 * aspects of the Hibernate runtime. Where no special requirements exist, 
 * methods delegate to {@link EmptyInterceptor}
 * 
 * Current responsibilities include the proper (re-)setting of {@link Details}
 * 
 * @author  Josh Moore, josh.moore at gmx.de
 * @version $Revision$, $Date$
 * @see 	EmptyInterceptor
 * @see 	Interceptor
 * @since   3.0-M3
 */
@RevisionDate("$Date$")
@RevisionNumber("$Revision$")
public class OmeroInterceptor implements Interceptor
{

	static volatile String last = null;
	
	static volatile int count = 1;
	
	private static Log log = LogFactory.getLog(OmeroInterceptor.class);
	
	private Interceptor EMPTY = EmptyInterceptor.INSTANCE;
	
	protected BasicSecuritySystem secSys;
		
	/** only public ctor, requires a non-null {@link BasicSecuritySystem} */
	public OmeroInterceptor( BasicSecuritySystem securitySystem )
	{
		Assert.notNull(securitySystem);
		this.secSys = securitySystem;
	}

	/** default logic, but we may want to use them eventually for 
	 * dependency-injection.
	 */
	public Object instantiate(String entityName, EntityMode entityMode, 
			Serializable id) throws CallbackException {
		
		debug("Intercepted instantiate.");
		return EMPTY.instantiate(entityName, entityMode, id);
	
	}
	
	/** default logic. */
	public boolean onLoad(Object entity, Serializable id, Object[] state, 
			String[] propertyNames, Type[] types) throws CallbackException {
		
		debug("Intercepted load.");
		return EMPTY.onLoad(entity, id, state, propertyNames, types);
		
	}
	
	/** default logic */
    public int[] findDirty(Object entity, Serializable id, 
    		Object[] currentState, Object[] previousState, 
    		String[] propertyNames, Type[] types)
    {
    	debug("Intercepted dirty check.");
       	return EMPTY.findDirty(
    			entity, id, currentState, previousState, 
    			propertyNames, types);
    }
    
    /** callsback to {@link BasicSecuritySystem#transientDetails(IObject)} for 
     * properly setting {@link IObject#getDetails() Details}
     */
    public boolean onSave(Object entity, Serializable id, 
    		Object[] state, 
    		String[] propertyNames, Type[] types)
    {
    	debug("Intercepted save.");
    	    	
    	if ( entity instanceof IObject )
    	{
    		IObject iobj = (IObject) entity;
    		int idx = HibernateUtils.detailsIndex( propertyNames );

    		secSys.markLockedIfNecessary( iobj );

    		// Get a new details based on the current context
    		Details d = secSys.transientDetails( iobj );
    		state[idx] = d;    		
    	}
    	
        return true; // transferDetails ALWAYS edits the new entity.
    }

    /** callsback to {@link BasicSecuritySystem#managedDetails(IObject, Details)} for 
     * properly setting {@link IObject#getDetails() Details}.
     */
    public boolean onFlushDirty(Object entity, Serializable id, 
    		Object[] currentState, Object[] previousState, 
    		String[] propertyNames, Type[] types)
    {
    	debug("Intercepted update.");
    	
    	boolean altered = false;
    	if ( entity instanceof IObject)
    	{
    		IObject iobj = (IObject) entity;		
    		int idx = HibernateUtils.detailsIndex( propertyNames );
    		
    		secSys.markLockedIfNecessary( iobj );
    		
    		altered |= resetDetails( iobj, currentState, previousState, idx );
    	}
        return altered;
    }

    /** default logic */
	public void onDelete(Object entity, Serializable id, 
			Object[] state, String[] propertyNames, Type[] types) 
	throws CallbackException 
	{ 
		debug("Intercepted delete."); 
    	EMPTY.onDelete(entity, id, state, propertyNames, types);
	}
    
    // ~ Collections (of interest)
	// =========================================================================
    public void onCollectionRecreate(Object collection, Serializable key) 
    throws CallbackException { debug("Intercepted collection recreate."); }
    
	public void onCollectionRemove(Object collection, Serializable key) 
	throws CallbackException { debug("Intercepted collection remove."); }
	
	public void onCollectionUpdate(Object collection, Serializable key) 
	throws CallbackException { debug("Intercepted collection update."); }
	
    // ~ Flush (currently unclear semantics)
	// =========================================================================
    public void preFlush(Iterator entities) throws CallbackException 
    {
    	debug("Intercepted preFlush.");
		EMPTY.preFlush(entities);
	}
    
    public void postFlush(Iterator entities) throws CallbackException 
	{
    	debug("Intercepted postFlush.");
		EMPTY.postFlush(entities);	
	}
    
    // ~ Serialization
    // =========================================================================
    
    private static final long serialVersionUID = 7616611615023614920L;
    
    private void readObject(ObjectInputStream s) 
    throws IOException, ClassNotFoundException
    {
        s.defaultReadObject();
    }
    
    // ~ Unused interface methods
	// =========================================================================

	public void afterTransactionBegin(Transaction tx) {}
	public void afterTransactionCompletion(Transaction tx) {}
	public void beforeTransactionCompletion(Transaction tx) {}

	public Object getEntity(String entityName, Serializable id) throws CallbackException {
		return EMPTY.getEntity(entityName, id);
	}

	public String getEntityName(Object object) throws CallbackException {
		return EMPTY.getEntityName(object);
	}

	public Boolean isTransient(Object entity) {
		return EMPTY.isTransient(entity);
	}
	
	public String onPrepareStatement(String sql) {
		// start
		if ( ! log.isDebugEnabled() )
		{
			return sql;
		}
		
		// from
		StringBuilder sb = new StringBuilder();
		String[] first  = sql.split("\\sfrom\\s");
		sb.append(first[0]);

		for (int i = 1; i < first.length; i++) {
			sb.append("\n from ");			
			sb.append(first[i]);
		}
		
		// where
		String[] second = sb.toString().split("\\swhere\\s");
		sb = new StringBuilder();
		sb.append(second[0]);

		for (int j = 1; j < second.length; j++) {
			sb.append("\n where ");
			sb.append(second[j]);
		}					
		
		return sb.toString();
		
	}
	
	// ~ Helpers
	// =========================================================================
	
	/** asks {@link BasicSecuritySystem} to create a new managed {@link Details}
	 * based on the previous state of this entity.
	 * 
	 * @param entity IObject to be updated
	 * @param currentState the possibly changed field data for this entity
	 * @param previousState the field data as seen in the db
	 * @param idx the index of Details in the state arrays.
	 */
	protected boolean resetDetails(IObject entity, Object[] currentState,
			Object[] previousState, int idx)
	{
		Details previous = (Details) previousState[idx];
		Details result = secSys.managedDetails( entity, previous ); 

		if ( previous != result )
		{
			currentState[idx] = result;
			return true;
		}
		
		return false;
	}
	
	protected void log(String msg)
	{
		if ( msg.equals(last))
		{
			count++;
		}
	
		else if ( log.isDebugEnabled() )
		{
			String times = " ( "+count+" times )";
			log.debug(msg+times);
			last = msg;
			count = 1;
		}
	}
    
    private void debug(String msg)
    {
    	if (log.isDebugEnabled())
    	{
    		log(msg);
    	}
    }
     
}