# 线程池原理与自定义线程池

JUC包已经实现了非常好用的线程池，在实际开发中大多都使用JUC包下的线程池。

自定义线程池可以帮助理解线程池的工作原理



没有线程池的弊端：多次创建、销毁线程会浪费大量时间。

解决方案：比如服务器，在服务器启动的时候就会（在线程池）创建一些线程，然后有请求访问的时候就会使用其中一个线程做任务，当请求操作结束后线程并不会销毁，而是将线程又回归到线程池中。一个线程可以循环可回收利用，可以处理多个任务。在服务器关闭的时候才会关闭线程。



线程池概念：

1. 任务队列：存储待执行的任务
2. 拒绝策略：请求任务过多，拒绝一部分任务
   - 抛出异常、直接丢弃、阻塞请求、临时队列
3. init（min）
4. active
5. max

min<=active<=max



线程池通用规则：

1. 首先线程池创建min个线程，如果处理线程过多会适量增加到active，更多则增加到max。如果线程数量超过了max则会添加到任务队列，如果任务队列满了则会执行拒绝策略。
2. 线程执行如果过了顶峰期（比如双十一）需要减少线程池中线程的数据到active（线程过多切换上下文会影响应用性能），如果有过多的任务则又增加线程数量。



线程池异步任务、批量任务



使用到线程池的开源项目：Quartz、Control-M



补充：[为什么阿里Java规约禁止使用Java内置Executors创建线程池？](https://www.cnblogs.com/ibigboy/p/11298004.html)

