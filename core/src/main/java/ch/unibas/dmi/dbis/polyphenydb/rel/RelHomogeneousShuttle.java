/*
 * This file is based on code taken from the Apache Calcite project, which was released under the Apache License.
 * The changes are released under the MIT license.
 *
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
 *
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2019 Databases and Information Systems Research Group, University of Basel, Switzerland
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package ch.unibas.dmi.dbis.polyphenydb.rel;


import ch.unibas.dmi.dbis.polyphenydb.rel.core.TableFunctionScan;
import ch.unibas.dmi.dbis.polyphenydb.rel.core.TableScan;
import ch.unibas.dmi.dbis.polyphenydb.rel.logical.LogicalAggregate;
import ch.unibas.dmi.dbis.polyphenydb.rel.logical.LogicalCorrelate;
import ch.unibas.dmi.dbis.polyphenydb.rel.logical.LogicalExchange;
import ch.unibas.dmi.dbis.polyphenydb.rel.logical.LogicalFilter;
import ch.unibas.dmi.dbis.polyphenydb.rel.logical.LogicalIntersect;
import ch.unibas.dmi.dbis.polyphenydb.rel.logical.LogicalJoin;
import ch.unibas.dmi.dbis.polyphenydb.rel.logical.LogicalMatch;
import ch.unibas.dmi.dbis.polyphenydb.rel.logical.LogicalMinus;
import ch.unibas.dmi.dbis.polyphenydb.rel.logical.LogicalProject;
import ch.unibas.dmi.dbis.polyphenydb.rel.logical.LogicalSort;
import ch.unibas.dmi.dbis.polyphenydb.rel.logical.LogicalUnion;
import ch.unibas.dmi.dbis.polyphenydb.rel.logical.LogicalValues;


/**
 * Visits all the relations in a homogeneous way: always redirects calls to {@code accept(RelNode)}.
 */
public class RelHomogeneousShuttle extends RelShuttleImpl {

    @Override
    public RelNode visit( LogicalAggregate aggregate ) {
        return visit( (RelNode) aggregate );
    }


    @Override
    public RelNode visit( LogicalMatch match ) {
        return visit( (RelNode) match );
    }


    @Override
    public RelNode visit( TableScan scan ) {
        return visit( (RelNode) scan );
    }


    @Override
    public RelNode visit( TableFunctionScan scan ) {
        return visit( (RelNode) scan );
    }


    @Override
    public RelNode visit( LogicalValues values ) {
        return visit( (RelNode) values );
    }


    @Override
    public RelNode visit( LogicalFilter filter ) {
        return visit( (RelNode) filter );
    }


    @Override
    public RelNode visit( LogicalProject project ) {
        return visit( (RelNode) project );
    }


    @Override
    public RelNode visit( LogicalJoin join ) {
        return visit( (RelNode) join );
    }


    @Override
    public RelNode visit( LogicalCorrelate correlate ) {
        return visit( (RelNode) correlate );
    }


    @Override
    public RelNode visit( LogicalUnion union ) {
        return visit( (RelNode) union );
    }


    @Override
    public RelNode visit( LogicalIntersect intersect ) {
        return visit( (RelNode) intersect );
    }


    @Override
    public RelNode visit( LogicalMinus minus ) {
        return visit( (RelNode) minus );
    }


    @Override
    public RelNode visit( LogicalSort sort ) {
        return visit( (RelNode) sort );
    }


    @Override
    public RelNode visit( LogicalExchange exchange ) {
        return visit( (RelNode) exchange );
    }
}