/**
 *
 */
package com.quinsoft.zeidon.standardoe;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.quinsoft.zeidon.ActivateFlags;
import com.quinsoft.zeidon.ActivateFromStream;
import com.quinsoft.zeidon.Application;
import com.quinsoft.zeidon.CreateEntityFlags;
import com.quinsoft.zeidon.CursorPosition;
import com.quinsoft.zeidon.Task;
import com.quinsoft.zeidon.View;
import com.quinsoft.zeidon.ZeidonException;
import com.quinsoft.zeidon.objectdefinition.DynamicViewAttributeConfiguration;
import com.quinsoft.zeidon.objectdefinition.ViewAttribute;
import com.quinsoft.zeidon.objectdefinition.ViewEntity;
import com.quinsoft.zeidon.objectdefinition.ViewOd;

/**
 * Reads all the OIs from a given stream in JSON format.
 *
 * @author dgc
 *
 */
class ActivateOisFromJsonStream
{
    private static final EnumSet<CreateEntityFlags> CREATE_FLAGS = EnumSet.of( CreateEntityFlags.fNO_SPAWNING,
                                                                               CreateEntityFlags.fIGNORE_MAX_CARDINALITY,
                                                                               CreateEntityFlags.fDONT_UPDATE_OI,
                                                                               CreateEntityFlags.fDONT_INITIALIZE_ATTRIBUTES,
                                                                               CreateEntityFlags.fIGNORE_PERMISSIONS );
    private final Task                    task;
    private final InputStream             stream;

    /**
     * Keep track of the options for this activate.
     */
    private final ActivateFromStream  options;

    /**
     * This keeps track of all the entities that are the sources for linked instances.
     * The key is the EntityKey.
     */
    private final Map<Object, EntityInstanceImpl> linkSources;

    private JsonParser                    jp;
    private Application                   application;
    private boolean                       incremental;
    private ViewOd                        viewOd;
    private View                          view;
    private List<View>                    returnList;
    private String version;
    private EnumSet<ActivateFlags> flags;

    ActivateOisFromJsonStream( ActivateFromStream options )
    {
        this.task = options.getTask();
        this.stream = options.getInputStream();
        returnList = new ArrayList<View>();
        linkSources = new HashMap<Object, EntityInstanceImpl>();
        this.options = options;
        viewOd = options.getViewOd();
        flags = options.getFlags();
    }

    public List<View> read()
    {
        try
        {
            JsonFactory jsonFactory = new JsonFactory();
            jp = jsonFactory.createParser( stream );
            jp.configure( JsonParser.Feature.AUTO_CLOSE_SOURCE, false );

            // Read the START_OBJECT
            JsonToken token = jp.nextToken();
            if ( token != JsonToken.START_OBJECT )
                throw new ZeidonException( "OI JSON stream doesn't start with object." );

            token = jp.nextToken();
            if ( token != JsonToken.FIELD_NAME )
                throw new ZeidonException( "OI JSON missing OI field name." );

            String fieldName = jp.getCurrentName();
            if ( fieldName.equals( ".meta"  ) )
            {
                readFileMeta();

                token = jp.nextToken();
                if ( token != JsonToken.START_ARRAY )
                    throw new ZeidonException( "OI JSON missing beginning of OI array." );

                while ( readOi() );
            }
            else
            {
                if ( viewOd == null )
                    throw new ZeidonException( "JSON stream appears to start with the root entity name (%s)" +
                                               " but the ViewOD has not been specified." );

                String rootName = viewOd.getRoot().getName();
                if ( ! fieldName.equals( rootName ) )
                    throw new ZeidonException( "The first field in the JSON stream must be the root entity name" +
                                               " (%) or '.meta' but was %s.", rootName, fieldName );

                view = task.activateEmptyObjectInstance( viewOd );
                returnList.add( view );

                while ( readSimpleOi() );
            }

            jp.close();
            view.reset();
        }
        catch ( Exception e )
        {
            ZeidonException ze = ZeidonException.wrapException( e );
            JsonLocation loc = jp.getCurrentLocation();
            JsonToken token = jp.getCurrentToken();
            ze.appendMessage( "Position line=%d col=%d, token=%s", loc.getLineNr(), loc.getColumnNr(),
                              token == null ? "No Token" : token.name() );
            throw ze;
        }

        return returnList;
    }

    private void readFileMeta() throws Exception
    {
        jp.nextToken();
        while ( jp.nextToken() != JsonToken.END_OBJECT )
        {
            String fieldName = jp.getCurrentName();
            jp.nextToken(); // Move to value.
            switch ( fieldName )
            {
                case "version":
                    version = jp.getValueAsString();
                    task.log().debug( "JSON version: %s", version );
                    break;

                case "date":
                    break;

                default:
                    task.log().warn( "Unknown .oimeta fieldname %s", fieldName );
            }
        }

        jp.nextToken();

    }

    private boolean readOi() throws Exception
    {
        JsonToken token = jp.nextToken();

        // If we find the end of the OI array then that's the end of OIs.
        if ( token == JsonToken.END_ARRAY )
            return false;  // No more OIs in the stream.

        if ( token != JsonToken.START_OBJECT )
            throw new ZeidonException( "OI JSON stream doesn't start with object." );

        token = jp.nextToken();

        String fieldName = jp.getCurrentName();
        if ( StringUtils.equals( fieldName, ".oimeta" ) )
            token = readOiMeta();
        else
            throw new ZeidonException( ".oimeta object not specified in JSON stream" );

        // If the token after reading the .oimeta is END_OBJECT then the OI is empty.
        if ( token != JsonToken.END_OBJECT )
        {
            fieldName = jp.getCurrentName();
            if ( !StringUtils.equals( fieldName, viewOd.getRoot().getName() ) )
                throw new ZeidonException( "First entity specified in OI (%s) is not the root (%s)", fieldName,
                                           viewOd.getRoot().getName() );

            readEntity( fieldName );
            token = jp.nextToken();
        }

        if ( token != JsonToken.END_OBJECT )
            throw new ZeidonException( "OI JSON stream doesn't end with object." );

        return true;  // Keep looking for OIs in the stream.
    }

    private boolean readSimpleOi() throws Exception
    {
        JsonToken token = jp.getCurrentToken();

        // If we find the end of the OI array then that's the end of OIs.
        if ( token == JsonToken.END_ARRAY || token == JsonToken.END_OBJECT )
            return false;  // No more OIs in the stream.

        String fieldName = jp.getCurrentName();

        assert token == JsonToken.FIELD_NAME;
        assert viewOd.getRoot().getName().equals( fieldName );

        // If the token after reading the .oimeta is END_OBJECT then the OI is empty.
        if ( token != JsonToken.END_OBJECT )
        {
            // readEntity expects the current token to be the opening { or [.
            // Skip over the field name.
            token = jp.nextToken();
            readEntity( fieldName );
            token = jp.nextToken();
        }

        if ( token != JsonToken.END_OBJECT )
            throw new ZeidonException( "OI JSON stream doesn't end with object." );

        return true;  // Keep looking for OIs in the stream.
    }

    private void readEntity( String entityName ) throws Exception
    {
        // Keeps track of whether the entity list starts with a [ or not.  If there
        // is no [ then we are done reading entities of this type when we find the
        // end of the object.
        boolean entityArray = false;
        int twinCount = 0;

        JsonToken token = jp.getCurrentToken();
        if ( token == JsonToken.START_ARRAY )
        {
            token = jp.nextToken();
            entityArray = true;  // Entity list started with [
        }

        assert token == JsonToken.START_OBJECT;

        ViewEntity viewEntity = viewOd.getViewEntity( entityName );

        // Read tokens until we find the token that ends the current list of entities.
        while ( ( token = jp.nextToken() ) != null )
        {
            twinCount++;

            if ( token == JsonToken.END_ARRAY )
                break;

            // If there are multiple twins then the token is START_OBJECT to
            // indicate a new EI.
            if ( token == JsonToken.START_OBJECT )
            {
                assert twinCount > 1; // Assert that we already created at least one EI.
                token = jp.nextToken();
            }

            assert token == JsonToken.FIELD_NAME;
            EntityInstanceImpl ei = (EntityInstanceImpl) view.cursor( viewEntity ).createEntity( CursorPosition.LAST, CREATE_FLAGS );

            // Read tokens until we find the token that ends the current entity.
            EntityMeta entityMeta = null;
            while ( ( token = jp.nextToken() ) != JsonToken.END_OBJECT )
            {
                String fieldName = jp.getCurrentName();
                if ( token != JsonToken.VALUE_STRING )
                    token = jp.nextToken();

                if ( StringUtils.equals( fieldName, ".meta" ) )
                {
                    entityMeta = readEntityMeta();

                    // Now that we have everything we can perform some processing.
                    if ( entityMeta.isLinkedSource )
                        linkSources.put( entityMeta.entityKey, ei );
                    else
                    if ( entityMeta.linkedSource != null )
                        ei.linkInstances( linkSources.get( entityMeta.linkedSource ) );

                    continue;
                }

                if ( fieldName.startsWith( "." ) )
                {
                    readAttributeMeta( ei, fieldName );
                    continue;
                }

                // Is this the start of an entity.
                if ( token == JsonToken.START_ARRAY || token == JsonToken.START_OBJECT )
                {
                    boolean recursiveChild = false;

                    // Validate that the entity name is valid.
                    ViewEntity childEntity = viewOd.getViewEntity( fieldName );
                    if ( childEntity.getParent() != viewEntity )
                    {
                        // Check to see the childEntity is a recursive child.
                        if ( childEntity.isRecursive() )
                        {
                            view.cursor( viewEntity ).setToSubobject();
                            recursiveChild = true;
                        }
                        else
                            throw new ZeidonException( "Parse error: %s is not a child of %s", fieldName,
                                                       entityName );
                    }

                    readEntity( fieldName );

                    if ( recursiveChild )
                        view.resetSubobject();

                    continue;
                }

                if ( StringUtils.equals( jp.getText(), fieldName ) )
                    // If jp points to attr name, get next token.
                    token = jp.nextToken();

                // This better be an attribute
                // Try getting the attribute.  We won't throw an exception (yet) if there
                // is no attribute with a matching name.
                ViewAttribute viewAttribute = viewEntity.getAttribute( fieldName, false );
                if ( viewAttribute == null )
                {
                    // We didn't find an attribute with a name matching fieldName.  Do we allow
                    // dynamic attributes for this entity?
                    if ( ! options.getAllowableDynamicEntities().contains( viewEntity.getName() ) )
                        viewEntity.getAttribute( fieldName ); // This will throw the exception.

                    // We are allowing dynamic attributes.  Create one.
                    DynamicViewAttributeConfiguration config = new DynamicViewAttributeConfiguration();
                    config.setAttributeName( fieldName );
                    viewAttribute = viewEntity.createDynamicViewAttribute( config );
                }

                ei.setInternalAttributeValue( viewAttribute, jp.getText(), false );
                if ( incremental )
                {
                    // Since incremental flags are set, assume the attribute hasn't been
                    // updated.  We'll be told later if it has.
                    AttributeValue attrib = ei.getInternalAttribute( viewAttribute );
                    attrib.setUpdated( false );
                }
            } // while ( ( token = jp.nextToken() ) != JsonToken.END_OBJECT )...

            // Now that we've updated everyting, set the flags.
            if ( entityMeta != null )
            {
                ei.setCreated( entityMeta.created );
                ei.setUpdated( entityMeta.updated );
                ei.setDeleted( entityMeta.deleted );
                ei.setIncluded( entityMeta.included );
                ei.setExcluded( entityMeta.excluded );
            }

            // If the entity list didn't start with a [ then there is only one entity
            // in the list of twins so exit.
            if ( entityArray == false )
                break;

        } // while ( ( token = jp.nextToken() ) != null )...
    }

    private void readAttributeMeta( EntityInstanceImpl ei, String fieldName ) throws JsonParseException, IOException
    {
        String attribName = fieldName.substring( 1 ); // Remove the ".".
        ViewAttribute viewAttribute = ei.getViewEntity().getAttribute( attribName );
        AttributeValue attrib = ei.getInternalAttribute( viewAttribute );

        while ( jp.nextToken() != JsonToken.END_OBJECT )
        {
            fieldName = jp.getCurrentName();

            if ( fieldName.equals( "updated" ) )
                attrib.setUpdated( true );
            else
                task.log().warn( "Unknown entity meta value %s", fieldName );
        }
    }

    private EntityMeta readEntityMeta() throws Exception
    {
        EntityMeta meta = new EntityMeta();
        while ( jp.nextToken() != JsonToken.END_OBJECT )
        {
            String fieldName = jp.getCurrentName();

            if ( fieldName.equals( "incrementals" ) )
                readIncrementals( meta );
            else
            if ( fieldName.equals( "isLinkedSource" ) )
                meta.isLinkedSource = true;
            else
            if ( fieldName.equals( "entityKey" ) )
                meta.entityKey = jp.getText();
            else
            if ( fieldName.equals( "linkedSource" ) )
                meta.linkedSource = jp.getText();
            else
                task.log().warn( "Unknown entity meta value %s", fieldName );
        }

        return meta;
    }

    private void readIncrementals( EntityMeta meta ) throws JsonParseException, IOException
    {
        String increStr = jp.getText().toLowerCase();

        meta.updated  = increStr.contains( "u" );
        meta.created  = increStr.contains( "c" );
        meta.deleted  = increStr.contains( "d" );
        meta.included = increStr.contains( "i" );
        meta.excluded = increStr.contains( "x" );
    }

    private JsonToken readOiMeta() throws Exception
    {
        String odName = null;
        jp.nextToken();
        while ( jp.nextToken() != JsonToken.END_OBJECT )
        {
            String fieldName = jp.getCurrentName();
            jp.nextToken(); // Move to value.
            if ( StringUtils.equals( fieldName, "application" ) )
                application = task.getApplication( jp.getValueAsString() );
            else if ( StringUtils.equals( fieldName, "odName" ) )
                odName = jp.getValueAsString(); // Save OD name for later.
            else if ( StringUtils.equals( fieldName, "incremental" ) )
                incremental = jp.getValueAsBoolean();
            else
                task.log().warn( "Unknown .oimeta fieldname %s", fieldName );
        }

        if ( odName == null )
            throw new ZeidonException( "ViewOD not specified in JSON .oimeta" );

        // We don't load the ViewOD until now because it's valid JSON to reorder
        // the values
        // in the .oimeta object.
        viewOd = application.getViewOd( task, odName );
        view = task.activateEmptyObjectInstance( viewOd );
        returnList.add( view );
        JsonToken token = jp.nextToken();

        // If the next token is FIELD_NAME then OI data is next so get the next token.
        // If it's not the the OI is EMPTY and token should be END_OBJECT.
        if ( token == JsonToken.FIELD_NAME )
            token = jp.nextToken();
        else
            assert token == JsonToken.END_OBJECT;

        return token;
    }

    private static class EntityMeta
    {
        public String linkedSource;
        public String entityKey;
        public boolean isLinkedSource;
        boolean updated  = false;
        boolean created  = false;
        boolean deleted  = false;
        boolean included = false;
        boolean excluded = false;
    }
}
