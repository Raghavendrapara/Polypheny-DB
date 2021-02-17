/*
 * Copyright 2019-2020 The Polypheny Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * This file incorporates code covered by the following terms:
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.polypheny.db.prepare;


import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.function.Predicate;
import org.polypheny.db.config.RuntimeConfig;
import org.polypheny.db.jdbc.JavaTypeFactoryImpl;
import org.polypheny.db.plan.RelOptPlanner;
import org.polypheny.db.rel.type.RelDataType;
import org.polypheny.db.rel.type.RelDataTypeFactory;
import org.polypheny.db.rel.type.RelDataTypeFactoryImpl;
import org.polypheny.db.schema.AggregateFunction;
import org.polypheny.db.schema.Function;
import org.polypheny.db.schema.FunctionParameter;
import org.polypheny.db.schema.PolyphenyDbSchema;
import org.polypheny.db.schema.ScalarFunction;
import org.polypheny.db.schema.Table;
import org.polypheny.db.schema.TableFunction;
import org.polypheny.db.schema.TableMacro;
import org.polypheny.db.schema.Wrapper;
import org.polypheny.db.schema.impl.ScalarFunctionImpl;
import org.polypheny.db.sql.SqlFunctionCategory;
import org.polypheny.db.sql.SqlIdentifier;
import org.polypheny.db.sql.SqlOperator;
import org.polypheny.db.sql.SqlOperatorTable;
import org.polypheny.db.sql.SqlSyntax;
import org.polypheny.db.sql.validate.SqlMoniker;
import org.polypheny.db.sql.validate.SqlMonikerImpl;
import org.polypheny.db.sql.validate.SqlMonikerType;
import org.polypheny.db.sql.validate.SqlNameMatcher;
import org.polypheny.db.sql.validate.SqlNameMatchers;
import org.polypheny.db.sql.validate.SqlUserDefinedAggFunction;
import org.polypheny.db.sql.validate.SqlUserDefinedFunction;
import org.polypheny.db.sql.validate.SqlUserDefinedTableFunction;
import org.polypheny.db.sql.validate.SqlUserDefinedTableMacro;
import org.polypheny.db.sql.validate.SqlValidatorUtil;
import org.polypheny.db.type.PolyType;
import org.polypheny.db.type.PolyTypeFamily;
import org.polypheny.db.type.checker.FamilyOperandTypeChecker;
import org.polypheny.db.type.checker.OperandTypes;
import org.polypheny.db.type.inference.InferTypes;
import org.polypheny.db.type.inference.PolyReturnTypeInference;
import org.polypheny.db.type.inference.ReturnTypes;
import org.polypheny.db.util.Optionality;
import org.polypheny.db.util.Util;


/**
 * Implementation of {@link org.polypheny.db.prepare.Prepare.CatalogReader} and also {@link SqlOperatorTable} based on
 * tables and functions defined schemas.
 */
public class PolyphenyDbCatalogReader implements Prepare.CatalogReader, Serializable {

    protected final PolyphenyDbSchema rootSchema;
    protected final RelDataTypeFactory typeFactory;
    private final List<List<String>> schemaPaths;
    protected final SqlNameMatcher nameMatcher;


    public PolyphenyDbCatalogReader( PolyphenyDbSchema rootSchema, List<String> defaultSchema, RelDataTypeFactory typeFactory ) {
        this(
                rootSchema,
                SqlNameMatchers.withCaseSensitive( RuntimeConfig.CASE_SENSITIVE.getBoolean() ),
                ImmutableList.of( Objects.requireNonNull( defaultSchema ), ImmutableList.of() ),
                typeFactory );
    }


    protected PolyphenyDbCatalogReader( PolyphenyDbSchema rootSchema, SqlNameMatcher nameMatcher, List<List<String>> schemaPaths, RelDataTypeFactory typeFactory ) {
        this.rootSchema = Objects.requireNonNull( rootSchema );
        this.nameMatcher = nameMatcher;
        this.schemaPaths =
                Util.immutableCopy( Util.isDistinct( schemaPaths )
                        ? schemaPaths
                        : new LinkedHashSet<>( schemaPaths ) );
        this.typeFactory = typeFactory;
    }


    @Override
    public PolyphenyDbCatalogReader withSchemaPath( List<String> schemaPath ) {
        return new PolyphenyDbCatalogReader( rootSchema, nameMatcher, ImmutableList.of( schemaPath, ImmutableList.of() ), typeFactory );
    }


    @Override
    public Prepare.PreparingTable getTable( final List<String> names ) {
        // First look in the default schema, if any. If not found, look in the root schema.
        PolyphenyDbSchema.TableEntry entry = SqlValidatorUtil.getTableEntry( this, names );
        if ( entry != null ) {
            final Table table = entry.getTable();
            if ( table instanceof Wrapper ) {
                final Prepare.PreparingTable relOptTable = ((Wrapper) table).unwrap( Prepare.PreparingTable.class );
                if ( relOptTable != null ) {
                    return relOptTable;
                }
            }
            return RelOptTableImpl.create( this, table.getRowType( typeFactory ), entry, null );
        }
        return null;
    }


    private Collection<Function> getFunctionsFrom( List<String> names ) {
        final List<Function> functions2 = new ArrayList<>();
        final List<List<String>> schemaNameList = new ArrayList<>();
        if ( names.size() > 1 ) {
            // Name qualified: ignore path. But we do look in "/catalog" and "/", the last 2 items in the path.
            if ( schemaPaths.size() > 1 ) {
                schemaNameList.addAll( Util.skip( schemaPaths ) );
            } else {
                schemaNameList.addAll( schemaPaths );
            }
        } else {
            for ( List<String> schemaPath : schemaPaths ) {
                PolyphenyDbSchema schema = SqlValidatorUtil.getSchema( rootSchema, schemaPath, nameMatcher );
                if ( schema != null ) {
                    schemaNameList.addAll( schema.getPath() );
                }
            }
        }
        for ( List<String> schemaNames : schemaNameList ) {
            PolyphenyDbSchema schema = SqlValidatorUtil.getSchema( rootSchema, Iterables.concat( schemaNames, Util.skipLast( names ) ), nameMatcher );
            if ( schema != null ) {
                final String name = Util.last( names );
                functions2.addAll( schema.getFunctions( name, true ) );
            }
        }
        return functions2;
    }


    @Override
    public RelDataType getNamedType( SqlIdentifier typeName ) {
        PolyphenyDbSchema.TypeEntry typeEntry = SqlValidatorUtil.getTypeEntry( getRootSchema(), typeName );
        if ( typeEntry != null ) {
            return typeEntry.getType().apply( typeFactory );
        } else {
            return null;
        }
    }


    @Override
    public List<SqlMoniker> getAllSchemaObjectNames( List<String> names ) {
        final PolyphenyDbSchema schema = SqlValidatorUtil.getSchema( rootSchema, names, nameMatcher );
        if ( schema == null ) {
            return ImmutableList.of();
        }
        final List<SqlMoniker> result = new ArrayList<>();

        // Add root schema if not anonymous
        if ( !schema.getName().equals( "" ) ) {
            result.add( moniker( schema, null, SqlMonikerType.SCHEMA ) );
        }

        final Map<String, PolyphenyDbSchema> schemaMap = schema.getSubSchemaMap();

        for ( String subSchema : schemaMap.keySet() ) {
            result.add( moniker( schema, subSchema, SqlMonikerType.SCHEMA ) );
        }

        for ( String table : schema.getTableNames() ) {
            result.add( moniker( schema, table, SqlMonikerType.TABLE ) );
        }

        final NavigableSet<String> functions = schema.getFunctionNames();
        for ( String function : functions ) { // views are here as well
            result.add( moniker( schema, function, SqlMonikerType.FUNCTION ) );
        }
        return result;
    }


    private SqlMonikerImpl moniker( PolyphenyDbSchema schema, String name, SqlMonikerType type ) {
        final List<String> path = schema.path( name );
        if ( path.size() == 1 && !schema.root().getName().equals( "" ) && type == SqlMonikerType.SCHEMA ) {
            type = SqlMonikerType.CATALOG;
        }
        return new SqlMonikerImpl( path, type );
    }


    @Override
    public List<List<String>> getSchemaPaths() {
        return schemaPaths;
    }


    @Override
    public Prepare.PreparingTable getTableForMember( List<String> names ) {
        return getTable( names );
    }


    @Override
    public RelDataType createTypeFromProjection( final RelDataType type, final List<String> columnNameList ) {
        return SqlValidatorUtil.createTypeFromProjection( type, columnNameList, typeFactory, nameMatcher.isCaseSensitive() );
    }


    @Override
    public void lookupOperatorOverloads( final SqlIdentifier opName, SqlFunctionCategory category, SqlSyntax syntax, List<SqlOperator> operatorList ) {
        if ( syntax != SqlSyntax.FUNCTION ) {
            return;
        }

        final Predicate<Function> predicate;
        if ( category == null ) {
            predicate = function -> true;
        } else if ( category.isTableFunction() ) {
            predicate = function -> function instanceof TableMacro || function instanceof TableFunction;
        } else {
            predicate = function -> !(function instanceof TableMacro || function instanceof TableFunction);
        }
        getFunctionsFrom( opName.names )
                .stream()
                .filter( predicate )
                .map( function -> toOp( opName, function ) )
                .forEachOrdered( operatorList::add );
    }


    private SqlOperator toOp( SqlIdentifier name, final Function function ) {
        return toOp( typeFactory, name, function );
    }


    /**
     * Converts a function to a {@link org.polypheny.db.sql.SqlOperator}.
     *
     * The {@code typeFactory} argument is technical debt; see [POLYPHENYDB-2082] Remove RelDataTypeFactory argument from SqlUserDefinedAggFunction constructor.
     */
    private static SqlOperator toOp( RelDataTypeFactory typeFactory, SqlIdentifier name, final Function function ) {
        List<RelDataType> argTypes = new ArrayList<>();
        List<PolyTypeFamily> typeFamilies = new ArrayList<>();
        for ( FunctionParameter o : function.getParameters() ) {
            final RelDataType type = o.getType( typeFactory );
            argTypes.add( type );
            typeFamilies.add( Util.first( type.getPolyType().getFamily(), PolyTypeFamily.ANY ) );
        }
        final FamilyOperandTypeChecker typeChecker = OperandTypes.family( typeFamilies, i -> function.getParameters().get( i ).isOptional() );
        final List<RelDataType> paramTypes = toSql( typeFactory, argTypes );
        if ( function instanceof ScalarFunction ) {
            return new SqlUserDefinedFunction( name, infer( (ScalarFunction) function ), InferTypes.explicit( argTypes ), typeChecker, paramTypes, function );
        } else if ( function instanceof AggregateFunction ) {
            return new SqlUserDefinedAggFunction(
                    name,
                    infer( (AggregateFunction) function ),
                    InferTypes.explicit( argTypes ),
                    typeChecker,
                    (AggregateFunction) function,
                    false,
                    false,
                    Optionality.FORBIDDEN,
                    typeFactory );
        } else if ( function instanceof TableMacro ) {
            return new SqlUserDefinedTableMacro( name, ReturnTypes.CURSOR, InferTypes.explicit( argTypes ), typeChecker, paramTypes, (TableMacro) function );
        } else if ( function instanceof TableFunction ) {
            return new SqlUserDefinedTableFunction( name, ReturnTypes.CURSOR, InferTypes.explicit( argTypes ), typeChecker, paramTypes, (TableFunction) function );
        } else {
            throw new AssertionError( "unknown function type " + function );
        }
    }


    private static PolyReturnTypeInference infer( final ScalarFunction function ) {
        return opBinding -> {
            final RelDataTypeFactory typeFactory = opBinding.getTypeFactory();
            final RelDataType type;
            if ( function instanceof ScalarFunctionImpl ) {
                type = ((ScalarFunctionImpl) function).getReturnType( typeFactory, opBinding );
            } else {
                type = function.getReturnType( typeFactory );
            }
            return toSql( typeFactory, type );
        };
    }


    private static PolyReturnTypeInference infer( final AggregateFunction function ) {
        return opBinding -> {
            final RelDataTypeFactory typeFactory = opBinding.getTypeFactory();
            final RelDataType type = function.getReturnType( typeFactory );
            return toSql( typeFactory, type );
        };
    }


    private static List<RelDataType> toSql( final RelDataTypeFactory typeFactory, List<RelDataType> types ) {
        return Lists.transform( types, type -> toSql( typeFactory, type ) );
    }


    private static RelDataType toSql( RelDataTypeFactory typeFactory, RelDataType type ) {
        if ( type instanceof RelDataTypeFactoryImpl.JavaType && ((RelDataTypeFactoryImpl.JavaType) type).getJavaClass() == Object.class ) {
            return typeFactory.createTypeWithNullability( typeFactory.createPolyType( PolyType.ANY ), true );
        }
        return JavaTypeFactoryImpl.toSql( typeFactory, type );
    }


    @Override
    public List<SqlOperator> getOperatorList() {
        return null;
    }


    @Override
    public PolyphenyDbSchema getRootSchema() {
        return rootSchema;
    }


    @Override
    public RelDataTypeFactory getTypeFactory() {
        return typeFactory;
    }


    @Override
    public void registerRules( RelOptPlanner planner ) throws Exception {
    }


    @Override
    public SqlNameMatcher nameMatcher() {
        return nameMatcher;
    }


    @Override
    public <C> C unwrap( Class<C> aClass ) {
        if ( aClass.isInstance( this ) ) {
            return aClass.cast( this );
        }
        return null;
    }
}

