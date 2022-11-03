/*
 * Copyright 2016 The Netty Project
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
package io.netty.channel;

import io.netty.util.IntSupplier;

/**
 * Select strategy interface.
 *
 * Provides the ability to control the behavior of the select loop. For example a blocking select
 * operation can be delayed or skipped entirely if there are events to process immediately.
 *
 * 选择策略接口 ..
 * 提供了一种能力去控制选择循环的行为 .. 例如一个阻塞的选择操作能够延迟或者完全跳过(如果事件需要理解处理) ..
 */
public interface SelectStrategy {

    /**
     * Indicates a blocking select should follow.
     *
     * 指示一个阻塞选择应该发生 ...
     */
    int SELECT = -1;
    /**
     * Indicates the IO loop should be retried, no blocking select to follow directly.
     *
     *  指示一个IO  循环应该重试, 允许直接的非阻塞选择 ...
     */
    int CONTINUE = -2;
    /**
     * Indicates the IO loop to poll for new events without blocking.
     *
     * 指示 IO 循环对当前新的事件进行轮询 而不进行等待 ...
     */
    int BUSY_WAIT = -3;

    /**
     * The {@link SelectStrategy} can be used to steer the outcome of a potential select
     * call.
     * 这个选择策略能够被用来决定 最终选择调用的结果 ..
     *
     * @param selectSupplier The supplier with the result of a select result.  一个包含选择结果的生成器
     * @param hasTasks true if tasks are waiting to be processed.   如果有任务等待执行 ..
     * @return {@link #SELECT} if the next step should be blocking select {@link #CONTINUE} if
     *         the next step should be to not select but rather jump back to the IO loop and try
     *         again. Any value >= 0 is treated as an indicator that work needs to be done.
     *
     *         如果下一步应该阻塞选择, 可以是select,
     *         如果不选择相反直接跳过进行IO 循环并重试 可以是continue ..
     *         任何一个大于0的数 能够作为一个指示器(需要完成工作的指示器) ...
     *
     */
    int calculateStrategy(IntSupplier selectSupplier, boolean hasTasks) throws Exception;
}
