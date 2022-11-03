/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.util.concurrent;
// 渐进式Future 监听器
//能够监听 进度 ..
public interface GenericProgressiveFutureListener<F extends ProgressiveFuture<?>> extends GenericFutureListener<F> {
    /**
     * Invoked when the operation has progressed.
     *
     * 当操作在进行中,执行 ...
     * @param progress the progress of the operation so far (cumulative) 到目前为止的进度( 累计的,渐增式) ..
     * @param total the number that signifies the end of the operation when {@code progress} reaches at it.
     *              {@code -1} if the end of operation is unknown.
     *              当progress 到达了 total(total 表示操作结束的值) ..
     *              如果为 -1 表示操作结束是未知的 ...
     *
     */
    void operationProgressed(F future, long progress, long total) throws Exception;
}
