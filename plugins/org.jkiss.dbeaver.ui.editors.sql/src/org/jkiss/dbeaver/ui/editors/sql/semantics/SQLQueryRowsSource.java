/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2023 DBeaver Corp and others
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jkiss.dbeaver.ui.editors.sql.semantics;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.model.DBUtils;
import org.jkiss.dbeaver.model.runtime.VoidProgressMonitor;
import org.jkiss.dbeaver.model.stm.STMTreeNode;
import org.jkiss.dbeaver.model.struct.DBSEntityAttribute;
import org.jkiss.dbeaver.model.struct.rdb.DBSTable;


abstract class SQLQueryRowsSource {
    private SQLQueryDataContext dataContext;
    
    public SQLQueryDataContext getDataContext() {
        if (this.dataContext == null) {
            throw new UnsupportedOperationException("Data context was not resolved for the rows source yet");
        } else {
            return this.dataContext;
        }
    }

    public SQLQueryDataContext propagateContext(SQLQueryDataContext context, SQLQueryRecognitionContext statistics) {
        return this.dataContext = this.propagateContextImpl(context, statistics);
    }
    
    protected abstract SQLQueryDataContext propagateContextImpl(SQLQueryDataContext context, SQLQueryRecognitionContext statistics);
}

abstract class SQLQuerySetOperationModel extends SQLQueryRowsSource {
    protected final SQLQueryRowsSource left, right;
    
    public SQLQuerySetOperationModel(SQLQueryRowsSource left, SQLQueryRowsSource right) {
        this.left = left;
        this.right = right;
    }
}

abstract class SQLQuerySetCorrespondingOperationModel extends SQLQuerySetOperationModel {
    private final List<SQLQuerySymbolEntry> correspondingColumnNames;
    
    public SQLQuerySetCorrespondingOperationModel(SQLQueryRowsSource left, SQLQueryRowsSource right, List<SQLQuerySymbolEntry> correspondingColumnNames) {
        super(left, right);
        this.correspondingColumnNames = correspondingColumnNames;
    }
    
    @Override
    protected SQLQueryDataContext propagateContextImpl(SQLQueryDataContext context, SQLQueryRecognitionContext statistics) {
        SQLQueryDataContext left = this.left.propagateContext(context, statistics);
        SQLQueryDataContext right = this.right.propagateContext(context, statistics);

        List<SQLQuerySymbol> resultColumns;
        boolean nonMatchingColumnSets = false;
        if (correspondingColumnNames.isEmpty()) { // require left and right to have the same tuples
            List<SQLQuerySymbol> leftColumns = left.getColumnsList();
            List<SQLQuerySymbol> rightColumns = right.getColumnsList();
            resultColumns = new ArrayList<>(Math.max(leftColumns.size(), rightColumns.size()));
            
            for (int i = 0; i < resultColumns.size(); i++) {
                if (i >= leftColumns.size()) {
                    resultColumns.add(rightColumns.get(i));
                    nonMatchingColumnSets = true;
                } else if (i >= rightColumns.size()) {
                    resultColumns.add(leftColumns.get(i));
                    nonMatchingColumnSets = true;
                } else {
                    resultColumns.add(leftColumns.get(i).merge(rightColumns.get(i)));
                }
            }
        } else { // require left and right to have columns subset as given with correspondingColumnNames
            resultColumns = new ArrayList<>(correspondingColumnNames.size());
            for (int i = 0; i < resultColumns.size(); i++) {
                SQLQuerySymbolEntry column = correspondingColumnNames.get(i);
                SQLQuerySymbolDefinition leftDef = left.resolveColumn(column.getName());
                SQLQuerySymbolDefinition rightDef = right.resolveColumn(column.getName());
                
                if (leftDef == null || rightDef == null) {
                    nonMatchingColumnSets = true;
                }
                
                column.getSymbol().setDefinition(column); // TODO combine multiple definitions
                resultColumns.add(column.getSymbol());
            }
        }
        
        if (nonMatchingColumnSets) {
            statistics.appendError((STMTreeNode)null, "UNION, EXCEPT and INTERSECT require subsets column tuples to match"); // TODO detailed messages per column
        }

        return correspondingColumnNames.isEmpty() ? left : context.overrideResultTuple(resultColumns); // TODO multiple definitions per symbol
    }
}

class SQLQueryIntersectModel extends SQLQuerySetCorrespondingOperationModel {
    public SQLQueryIntersectModel(SQLQueryRowsSource left, SQLQueryRowsSource right, List<SQLQuerySymbolEntry> correspondingColumnNames) {
        super(left, right, correspondingColumnNames);
    }
}

class SQLQueryUnionModel extends SQLQuerySetCorrespondingOperationModel {
    public SQLQueryUnionModel(SQLQueryRowsSource left, SQLQueryRowsSource right, List<SQLQuerySymbolEntry> correspondingColumnNames) {
        super(left, right, correspondingColumnNames);
    }  
}

class SQLQueryExceptModel extends SQLQuerySetCorrespondingOperationModel {
    public SQLQueryExceptModel(SQLQueryRowsSource left, SQLQueryRowsSource right, List<SQLQuerySymbolEntry> correspondingColumnNames) {
        super(left, right, correspondingColumnNames);
    }
}

class SQLQueryCrossJoinModel extends SQLQuerySetOperationModel {
    public SQLQueryCrossJoinModel(SQLQueryRowsSource left, SQLQueryRowsSource right) {
        super(left, right);
    }
    
    @Override
    protected SQLQueryDataContext propagateContextImpl(SQLQueryDataContext context, SQLQueryRecognitionContext statistics) {
        return this.left.propagateContext(context, statistics).combine(this.right.propagateContext(context, statistics));
    }
}

class SQLQueryNaturalJoinModel extends SQLQuerySetOperationModel {
    private final SQLQueryValueExpression condition;
    private final List<SQLQuerySymbolEntry> columsToJoin;
    
    public SQLQueryNaturalJoinModel(SQLQueryRowsSource left, SQLQueryRowsSource right, SQLQueryValueExpression condition) {
        super(left, right);
        this.condition = condition;
        this.columsToJoin = null;
    }
    
    public SQLQueryNaturalJoinModel(SQLQueryRowsSource left, SQLQueryRowsSource right, List<SQLQuerySymbolEntry> columsToJoin) {
        super(left, right);
        this.condition = null;
        this.columsToJoin = columsToJoin;
    }
    
    @Override
    protected SQLQueryDataContext propagateContextImpl(SQLQueryDataContext context, SQLQueryRecognitionContext statistics) {
        SQLQueryDataContext left = this.left.propagateContext(context, statistics);
        SQLQueryDataContext right = this.right.propagateContext(context, statistics);
        if (this.columsToJoin != null) {
            for (SQLQuerySymbolEntry column: columsToJoin) {
                SQLQuerySymbol symbol = column.getSymbol();
                SQLQuerySymbolDefinition leftColumnDef = left.resolveColumn(column.getName());
                SQLQuerySymbolDefinition rightColumnDef = right.resolveColumn(column.getName());
                if (leftColumnDef != null && rightColumnDef != null) {
                    symbol.setSymbolClass(SQLQuerySymbolClass.COLUMN); 
                    symbol.setDefinition(column); // TODO multiple definitions per symbol
                } else {
                    if (leftColumnDef != null) {
                        statistics.appendError(column, "Column not found to the left of join");
                    } else {
                        statistics.appendError(column, "Column not found to the right of join");
                    }
                    symbol.setSymbolClass(SQLQuerySymbolClass.ERROR);
                }
            }
        }
        
        SQLQueryDataContext combinedContext = left.combine(right);
        if (this.condition != null) {
            this.condition.propagateContext(combinedContext, statistics);
        }
        return combinedContext;
    }
}

class SQLTableValueRowsSource extends SQLQueryRowsSource {

    public SQLTableValueRowsSource() {
        // TODO
    }
    
    
    @Override
    protected SQLQueryDataContext propagateContextImpl(SQLQueryDataContext context, SQLQueryRecognitionContext statistics) {
        // TODO Auto-generated method stub
        return null;
    }    
}

class SQLQueryCorrelatedRowsSource extends SQLQueryRowsSource {
    private final SQLQueryRowsSource source;
    private final SQLQuerySymbolEntry alias;
    private final List<SQLQuerySymbolEntry> correlationColumNames;
    
    public SQLQueryCorrelatedRowsSource(SQLQueryRowsSource source, SQLQuerySymbolEntry alias, List<SQLQuerySymbolEntry> correlationColumNames) {
        this.source = source;
        this.alias = alias;
        this.correlationColumNames = correlationColumNames;
    }
    
    @Override
    protected SQLQueryDataContext propagateContextImpl(SQLQueryDataContext context, SQLQueryRecognitionContext statistics) {
        context = source.propagateContext(context, statistics).extendWithTableAlias(this.alias.getSymbol(), source);
        this.alias.getSymbol().setDefinition(this.alias);
        this.alias.getSymbol().setSymbolClass(SQLQuerySymbolClass.TABLE_ALIAS);

        if (correlationColumNames.size() > 0) {
            List<SQLQuerySymbol> columns = new ArrayList<>(context.getColumnsList());
            for (int i = 0; i < columns.size() && i < correlationColumNames.size(); i++) {
                SQLQuerySymbolEntry correlatedNameDef = correlationColumNames.get(i);
                SQLQuerySymbol correlatedName = correlatedNameDef.getSymbol();
                correlatedNameDef.setDefinition(columns.get(i).getDefinition());
                correlatedName.setDefinition(correlatedNameDef);
                columns.set(i, correlatedName);
            }
            context = context.overrideResultTuple(columns);
        }
        
        return context;
    }
}

class SQLQueryTableDataModel extends SQLQueryRowsSource implements SQLQuerySymbolDefinition { 
    private final SQLQueryQualifiedName name;
    private DBSTable table = null;
   
    public SQLQueryTableDataModel(SQLQueryQualifiedName name) {
        this.name = name;
    }

    public SQLQueryQualifiedName getName() {
        return this.name;
    }
    
    public DBSTable getTable() {
        return this.table;
    }
    
    @Override
    public SQLQuerySymbolClass getSymbolClass() {
        return this.table != null ? SQLQuerySymbolClass.TABLE : SQLQuerySymbolClass.ERROR; // TODO depends on connection availability
    }
    
    private SQLQuerySymbol prepareColumnSymbol(DBSEntityAttribute attr) {
        SQLQuerySymbol symbol = new SQLQuerySymbol(attr.getName());
        symbol.setDefinition(new SQLQuerySymbolByDbObjectDefinition(attr, SQLQuerySymbolClass.COLUMN));
        return symbol;
    }

    @Override
    protected SQLQueryDataContext propagateContextImpl(SQLQueryDataContext context, SQLQueryRecognitionContext statistics) {
        this.table = context.findRealTable(name.toListOfStrings());
                
        if (this.table != null) { 
            this.name.setDefinition(table);
        
            context = context.extendWithRealTable(this.table, this);
            try {
                List<SQLQuerySymbol> columns = this.table.getAttributes(new VoidProgressMonitor()).stream()
                                                   .filter(a -> !DBUtils.isHiddenObject(a))
                                                   .map(this::prepareColumnSymbol)
                                                   .collect(Collectors.toList());
                context = context.overrideResultTuple(columns);
            } catch (DBException ex) {
                statistics.appendError(this.name.entityName, "Failed to resolve table", ex);
            }
        } else {
            this.name.setSymbolClass(SQLQuerySymbolClass.ERROR);
            statistics.appendError(this.name.entityName, "Table not found");
        }
        return context;
    }
}

class SQLQuerySelectionFilterModel extends SQLQueryRowsSource { // see tableExpression
    private final SQLQueryRowsSource fromSource;
    private final SQLQueryValueExpression whereClause, havingClause, groupByClause, orderByClause;

    public SQLQuerySelectionFilterModel(SQLQueryRowsSource fromSource, 
            SQLQueryValueExpression whereClause, SQLQueryValueExpression havingClause, 
            SQLQueryValueExpression groupByClause, SQLQueryValueExpression orderByClause
        ) {
        this.fromSource = fromSource;
        this.whereClause = whereClause;
        this.havingClause = havingClause;
        this.groupByClause = groupByClause;
        this.orderByClause = orderByClause;
    }

    @Override
    protected SQLQueryDataContext propagateContextImpl(SQLQueryDataContext context, SQLQueryRecognitionContext statistics) {
        SQLQueryDataContext result = fromSource.propagateContext(context, statistics);
        
        if (this.whereClause != null) {
            this.whereClause.propagateContext(result, statistics);
        }
        if (this.havingClause != null) {
            this.havingClause.propagateContext(result, statistics);
        }
        if (this.groupByClause != null) {
            this.groupByClause.propagateContext(result, statistics);
        }
        if (this.orderByClause != null) {
            this.orderByClause.propagateContext(result, statistics);
        }
             
        return result;
    }
}

class SQLQueryProjectionModel extends SQLQueryRowsSource {
    private final SQLQueryRowsSource fromSource; // from tableExpression
    private final SQLQuerySelectionResultModel result; // selectList
    
    public SQLQueryProjectionModel(SQLQueryRowsSource fromSource, SQLQuerySelectionResultModel result) {
        this.result = result;
        this.fromSource = fromSource;
    }
    
    @Override
    protected SQLQueryDataContext propagateContextImpl(SQLQueryDataContext context, SQLQueryRecognitionContext statistics) {
        context = fromSource.propagateContext(context, statistics);
        List<SQLQuerySymbol> resultColumns = this.result.expandColumns(context, statistics); 
        return context.overrideResultTuple(resultColumns).hideSources();
    }
}
