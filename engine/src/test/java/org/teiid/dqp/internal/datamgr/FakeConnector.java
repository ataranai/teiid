/*
 * JBoss, Home of Professional Open Source.
 * See the COPYRIGHT.txt file distributed with this work for information
 * regarding copyright ownership.  Some portions may be licensed
 * to Red Hat, Inc. under one or more contributor license agreements.
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 */

package org.teiid.dqp.internal.datamgr;

import java.util.Arrays;
import java.util.List;

import junit.framework.Assert;

import org.teiid.language.Command;
import org.teiid.language.QueryExpression;
import org.teiid.metadata.RuntimeMetadata;
import org.teiid.translator.TranslatorException;
import org.teiid.translator.DataNotAvailableException;
import org.teiid.translator.Execution;
import org.teiid.translator.ExecutionContext;
import org.teiid.translator.ExecutionFactory;
import org.teiid.translator.ResultSetExecution;
import org.teiid.translator.UpdateExecution;

public class FakeConnector extends ExecutionFactory {
	private static final int RESULT_SIZE = 5;
	
	private boolean executeBlocks;
    private boolean nextBatchBlocks;
    private boolean returnsFinalBatch;
    private boolean driverThrowsExceptionOnCancel;
    private long simulatedBatchRetrievalTime = 1000L;
    private ClassLoader classloader;
    
    private int connectionCount;
    private int executionCount;
    
    public int getConnectionCount() {
		return connectionCount;
	}
    
    public int getExecutionCount() {
		return executionCount;
	}
    
    @Override
    public Execution createExecution(Command command, ExecutionContext executionContext, RuntimeMetadata metadata, Object connection) throws TranslatorException {
    	executionCount++;
        return new FakeBlockingExecution(executionContext);
    }
    
    public Object getConnection() {
        return new FakeConnection();
    }
    
    @Override
    public Object getConnection(Object factory) throws TranslatorException {
    	return factory;
    }
    
    @Override
    public void closeConnection(Object connection, Object factory) {
    }
	
    private class FakeConnection {
    	public FakeConnection() {
			connectionCount++;
		}
    	
        public boolean released = false;
        public void close() {
            Assert.assertFalse("The connection should not be released more than once", released); //$NON-NLS-1$
            released = true;
        }
    }   
    
    private final class FakeBlockingExecution implements ResultSetExecution, UpdateExecution {
        private boolean closed = false;
        private boolean cancelled = false;
        private int rowCount;
        ExecutionContext ec;
        public FakeBlockingExecution(ExecutionContext ec) {
            this.ec = ec;
        }
        public void execute(QueryExpression query, int maxBatchSize) throws TranslatorException {
            if (executeBlocks) {
                waitForCancel();
            }
            if (classloader != null) {
            	Assert.assertSame(classloader, Thread.currentThread().getContextClassLoader());
            }
        }
        public synchronized void cancel() throws TranslatorException {
            cancelled = true;
            this.notify();
        }
        public void close() {
            Assert.assertFalse("The execution should not be closed more than once", closed); //$NON-NLS-1$
            closed = true;
        }
        @Override
        public void execute() throws TranslatorException {
            ec.addWarning(new Exception("Some warning")); //$NON-NLS-1$
        }
        @Override
        public List next() throws TranslatorException, DataNotAvailableException {
        	if (nextBatchBlocks) {
                waitForCancel();
            }
            if (this.rowCount >= RESULT_SIZE || returnsFinalBatch) {
            	return null;
            }
            this.rowCount++;
            return Arrays.asList(this.rowCount - 1);
        }
        private synchronized void waitForCancel() throws TranslatorException {
            try {
                this.wait(simulatedBatchRetrievalTime);
                if (cancelled && driverThrowsExceptionOnCancel) {
                    throw new TranslatorException("Request cancelled"); //$NON-NLS-1$
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
		@Override
		public int[] getUpdateCounts() throws DataNotAvailableException,
				TranslatorException {
			return new int[] {1};
		}
    }

	public boolean isExecuteBlocks() {
		return executeBlocks;
	}
	public void setExecuteBlocks(boolean executeBlocks) {
		this.executeBlocks = executeBlocks;
	}
	public boolean isNextBatchBlocks() {
		return nextBatchBlocks;
	}
	public void setNextBatchBlocks(boolean nextBatchBlocks) {
		this.nextBatchBlocks = nextBatchBlocks;
	}
	public boolean isReturnsFinalBatch() {
		return returnsFinalBatch;
	}
	public void setReturnsFinalBatch(boolean returnsFinalBatch) {
		this.returnsFinalBatch = returnsFinalBatch;
	}
	public boolean isDriverThrowsExceptionOnCancel() {
		return driverThrowsExceptionOnCancel;
	}
	public void setDriverThrowsExceptionOnCancel(
			boolean driverThrowsExceptionOnCancel) {
		this.driverThrowsExceptionOnCancel = driverThrowsExceptionOnCancel;
	}
	public long getSimulatedBatchRetrievalTime() {
		return simulatedBatchRetrievalTime;
	}
	public void setSimulatedBatchRetrievalTime(long simulatedBatchRetrievalTime) {
		this.simulatedBatchRetrievalTime = simulatedBatchRetrievalTime;
	}
	
	public void setClassloader(ClassLoader classloader) {
		this.classloader = classloader;
	}
}