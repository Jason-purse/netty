/*
 * Copyright 2017 The Netty Project
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
package io.netty.buffer;
// 对于netty 关注的就是使用堆内存的数量 以及使用直接内存的数量
// 由于这些性质,它可以组合composite ByteBuf,或者零拷贝ByteBuf ... 又或是池化 / 非池化的ByteBuf..
public interface ByteBufAllocatorMetric {
    /**
     * Returns the number of bytes of heap memory used by a {@link ByteBufAllocator} or {@code -1} if unknown.
     */
    long usedHeapMemory();

    /**
     * Returns the number of bytes of direct memory used by a {@link ByteBufAllocator} or {@code -1} if unknown.
     */
    long usedDirectMemory();
}
