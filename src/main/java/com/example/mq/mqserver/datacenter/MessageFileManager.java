package com.example.mq.mqserver.datacenter;

import com.example.mq.common.BinaryTool;
import com.example.mq.common.MqException;
import com.example.mq.mqserver.core.MSGQueue;
import com.example.mq.mqserver.core.Message;

import java.io.*;
import java.util.LinkedList;
import java.util.Scanner;

/*
 * 通过这个类, 来针对硬盘上的消息进行管理
 */
public class MessageFileManager {
    // 定义一个内部类, 来表示该队列的统计信息
    // 有限考虑使用 static, 静态内部类.
    static public class Stat {
        // 此处直接定义成 public, 就不再搞 get set 方法了.
        // 对于这样的简单的类, 就直接使用成员, 类似于 C 的结构体了.
        public int totalCount;  // 总消息数量
        public int validCount;  // 有效消息数量
    }

    public void init() {
        // 暂时不需要做啥额外的初始化工作, 以备后续扩展
    }

    // 预定消息文件所在的目录和文件名
    // 这个方法, 用来获取到指定队列对应的消息文件所在路径
    private String getQueueDir(String queueName) {
        return "./data/" + queueName;
    }

    // 这个方法用来获取该队列的消息数据文件路径
    // 注意, 二进制文件, 使用 txt 作为后缀, 不太合适. txt 一般表示文本. 此处咱们也就不改.
    // .bin / .dat
    private String getQueueDataPath(String queueName) {
        return getQueueDir(queueName) + "/queue_data.txt";
    }

    // 这个方法用来获取该队列的消息统计文件路径
    private String getQueueStatPath(String queueName) {
        return getQueueDir(queueName) + "/queue_stat.txt";
    }

    private Stat readStat(String queueName) {
        // 由于当前的消息统计文件是文本文件, 可以直接使用 Scanner 来读取文件内容
        Stat stat = new Stat();
        try (InputStream inputStream = new FileInputStream(getQueueStatPath(queueName))) {
            Scanner scanner = new Scanner(inputStream);
            stat.totalCount = scanner.nextInt();
            stat.validCount = scanner.nextInt();
            return stat;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void writeStat(String queueName, Stat stat) {
        // 使用 PrintWrite 来写文件.
        // OutputStream 打开文件, 默认情况下, 会直接把原文件清空. 此时相当于新的数据覆盖了旧的.
        try (OutputStream outputStream = new FileOutputStream(getQueueStatPath(queueName))) {
            PrintWriter printWriter = new PrintWriter(outputStream);
            printWriter.write(stat.totalCount + "\t" + stat.validCount);
            printWriter.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 创建队列对应的文件和目录
    public void createQueueFiles(String queueName) throws IOException {
        // 1. 先创建队列对应的消息目录
        File baseDir = new File(getQueueDir(queueName));
        if (!baseDir.exists()) {
            // 不存在, 就创建这个目录
            boolean ok = baseDir.mkdirs();
            if (!ok) {
                throw new IOException("创建目录失败! baseDir=" + baseDir.getAbsolutePath());
            }
        }
        // 2. 创建队列数据文件
        File queueDataFile = new File(getQueueDataPath(queueName));
        if (!queueDataFile.exists()) {
            boolean ok = queueDataFile.createNewFile();
            if (!ok) {
                throw new IOException("创建文件失败! queueDataFile=" + queueDataFile.getAbsolutePath());
            }
        }
        // 3. 创建消息统计文件
        File queueStatFile = new File(getQueueStatPath(queueName));
        if (!queueStatFile.exists()) {
            boolean ok = queueStatFile.createNewFile();
            if (!ok) {
                throw new IOException("创建文件失败! queueStatFile=" + queueStatFile.getAbsolutePath());
            }
        }
        // 4. 给消息统计文件, 设定初始值. 0\t0
        Stat stat = new Stat();
        stat.totalCount = 0;
        stat.validCount = 0;
        writeStat(queueName, stat);
    }

    // 删除队列的目录和文件.
    // 队列也是可以被删除的. 当队列删除之后, 对应的消息文件啥的, 自然也要随之删除.
    public void destroyQueueFiles(String queueName) throws IOException {
        // 先删除里面的文件, 再删除目录.
        File queueDataFile = new File(getQueueDataPath(queueName));
        boolean ok1 = queueDataFile.delete();
        File queueStatFile = new File(getQueueStatPath(queueName));
        boolean ok2 = queueStatFile.delete();
        File baseDir = new File(getQueueDir(queueName));
        boolean ok3 = baseDir.delete();
        if (!ok1 || !ok2 || !ok3) {
            // 有任意一个删除失败, 都算整体删除失败.
            throw new IOException("删除队列目录和文件失败! baseDir=" + baseDir.getAbsolutePath());
        }
    }

    // 检查队列的目录和文件是否存在.
    // 比如后续有生产者给 broker server 生产消息了, 这个消息就可能需要记录到文件上(取决于消息是否要持久化)
    public boolean checkFilesExits(String queueName) {
        // 判定队列的数据文件和统计文件是否都存在!!
        File queueDataFile = new File(getQueueDataPath(queueName));
        if (!queueDataFile.exists()) {
            return false;
        }
        File queueStatFile = new File(getQueueStatPath(queueName));
        if (!queueStatFile.exists()) {
            return false;
        }
        return true;
    }

    // 这个方法用来把一个新的消息, 放到队列对应的文件中.
    // queue 表示要把消息写入的队列. message 则是要写的消息.
    public void sendMessage(MSGQueue queue, Message message) throws MqException, IOException {
        // 1. 检查一下当前要写入的队列对应的文件是否存在.
        if (!checkFilesExits(queue.getName())) {
            throw new MqException("[MessageFileManager] 队列对应的文件不存在! queueName=" + queue.getName());
        }
        // 2. 把 Message 对象, 进行序列化, 转成二进制的字节数组.
        byte[] messageBinary = BinaryTool.toBytes(message);
        synchronized (queue) {
            // 3. 先获取到当前的队列数据文件的长度, 用这个来计算出该 Message 对象的 offsetBeg 和 offsetEnd
            // 把新的 Message 数据, 写入到队列数据文件的末尾. 此时 Message 对象的 offsetBeg , 就是当前文件长度 + 4
            // offsetEnd 就是当前文件长度 + 4 + message 自身长度.
            File queueDataFile = new File(getQueueDataPath(queue.getName()));
            // 通过这个方法 queueDataFile.length() 就能获取到文件的长度. 单位字节.
            message.setOffsetBeg(queueDataFile.length() + 4);
            message.setOffsetEnd(queueDataFile.length() + 4 + messageBinary.length);
            // 4. 写入消息到数据文件, 注意, 是追加写入到数据文件末尾.
            try (OutputStream outputStream = new FileOutputStream(queueDataFile, true)) {
                try (DataOutputStream dataOutputStream = new DataOutputStream(outputStream)) {
                    // 接下来要先写当前消息的长度, 占据 4 个字节的~~
                    dataOutputStream.writeInt(messageBinary.length);
                    // 写入消息本体
                    dataOutputStream.write(messageBinary);
                }
            }
            // 5. 更新消息统计文件
            Stat stat = readStat(queue.getName());
            stat.totalCount += 1;
            stat.validCount += 1;
            writeStat(queue.getName(), stat);
        }
    }

    // 这个是删除消息的方法.
    // 这里的删除是逻辑删除, 也就是把硬盘上存储的这个数据里面的那个 isValid 属性, 设置成 0
    // 1. 先把文件中的这一段数据, 读出来, 还原回 Message 对象;
    // 2. 把 isValid 改成 0;
    // 3. 把上述数据重新写回到文件.
    // 此处这个参数中的 message 对象, 必须得包含有效的 offsetBeg 和 offsetEnd
    public void deleteMessage(MSGQueue queue, Message message) throws IOException, ClassNotFoundException {
        synchronized (queue) {
            try (RandomAccessFile randomAccessFile = new RandomAccessFile(getQueueDataPath(queue.getName()), "rw")) {
                // 1. 先从文件中读取对应的 Message 数据.
                byte[] bufferSrc = new byte[(int) (message.getOffsetEnd() - message.getOffsetBeg())];
                randomAccessFile.seek(message.getOffsetBeg());
                randomAccessFile.read(bufferSrc);
                // 2. 把当前读出来的二进制数据, 转换回成 Message 对象
                Message diskMessage = (Message) BinaryTool.fromBytes(bufferSrc);
                // 3. 把 isValid 设置为无效.
                diskMessage.setIsValid((byte) 0x0);
                // 此处不需要给参数的这个 message 的 isValid 设为 0, 因为这个参数代表的是内存中管理的 Message 对象
                // 而这个对象马上也要被从内存中销毁了.
                // 4. 重新写入文件
                byte[] bufferDest = BinaryTool.toBytes(diskMessage);
                // 虽然上面已经 seek 过了, 但是上面 seek 完了之后, 进行了读操作, 这一读, 就导致, 文件光标往后移动, 移动到
                // 下一个消息的位置了. 因此要想让接下来的写入, 能够刚好写回到之前的位置, 就需要重新调整文件光标.
                randomAccessFile.seek(message.getOffsetBeg());
                randomAccessFile.write(bufferDest);
                // 通过上述这通折腾, 对于文件来说, 只是有一个字节发生改变而已了~~
            }
            // 不要忘了, 更新统计文件!! 把一个消息设为无效了, 此时有效消息个数就需要 - 1
            Stat stat = readStat(queue.getName());
            if (stat.validCount > 0) {
                stat.validCount -= 1;
            }
            writeStat(queue.getName(), stat);
        }
    }

    // 使用这个方法, 从文件中, 读取出所有的消息内容, 加载到内存中(具体来说是放到一个链表里)
    // 这个方法, 准备在程序启动的时候, 进行调用.
    // 这里使用一个 LinkedList, 主要目的是为了后续进行头删操作.
    // 这个方法的参数, 只是一个 queueName 而不是 MSGQueue 对象. 因为这个方法不需要加锁, 只使用 queueName 就够了.
    // 由于该方法是在程序启动时调用, 此时服务器还不能处理请求呢~~ 不涉及多线程操作文件.
    public LinkedList<Message> loadAllMessageFromQueue(String queueName) throws IOException, MqException, ClassNotFoundException {
        LinkedList<Message> messages = new LinkedList<>();
        try (InputStream inputStream = new FileInputStream(getQueueDataPath(queueName))) {
            try (DataInputStream dataInputStream = new DataInputStream(inputStream)) {
                // 这个变量记录当前文件光标.
                long currentOffset = 0;
                // 一个文件中包含了很多消息, 此处势必要循环读取.
                while (true) {
                    // 1. 读取当前消息的长度, 这里的 readInt 可能会读到文件的末尾(EOF)
                    //    readInt 方法, 读到文件末尾, 会抛出 EOFException 异常. 这一点和之前的很多流对象不太一样.
                    int messageSize = dataInputStream.readInt();
                    // 2. 按照这个长度, 读取消息内容
                    byte[] buffer = new byte[messageSize];
                    int actualSize = dataInputStream.read(buffer);
                    if (messageSize != actualSize) {
                        // 如果不匹配, 说明文件有问题, 格式错乱了!!
                        throw new MqException("[MessageFileManager] 文件格式错误! queueName=" + queueName);
                    }
                    // 3. 把这个读到的二进制数据, 反序列化回 Message 对象
                    Message message = (Message) BinaryTool.fromBytes(buffer);
                    // 4. 判定一下看看这个消息对象, 是不是无效对象.
                    if (message.getIsValid() != 0x1) {
                        // 无效数据, 直接跳过.
                        // 虽然消息是无效数据, 但是 offset 不要忘记更新.
                        currentOffset += (4 + messageSize);
                        continue;
                    }
                    // 5. 有效数据, 则需要把这个 Message 对象加入到链表中. 加入之前还需要填写 offsetBeg 和 offsetEnd
                    //    进行计算 offset 的时候, 需要知道当前文件光标的位置的. 由于当下使用的 DataInputStream 并不方便直接获取到文件光标位置
                    //    因此就需要手动计算下文件光标.
                    message.setOffsetBeg(currentOffset + 4);
                    message.setOffsetEnd(currentOffset + 4 + messageSize);
                    currentOffset += (4 + messageSize);
                    messages.add(message);
                }
            } catch (EOFException e) {
                // 这个 catch 并非真是处理 "异常", 而是处理 "正常" 的业务逻辑. 文件读到末尾, 会被 readInt 抛出该异常.
                // 这个 catch 语句中也不需要做啥特殊的事情
                System.out.println("[MessageFileManager] 恢复 Message 数据完成!");
            }
        }
        return messages;
    }

    // 检查当前是否要针对该队列的消息数据文件进行 GC
    public boolean checkGC(String queueName) {
        // 判定是否要 GC, 是根据总消息数和有效消息数. 这两个值都是在 消息统计文件 中的.
        Stat stat = readStat(queueName);
        if (stat.totalCount > 2000 && (double)stat.validCount / (double)stat.totalCount < 0.5) {
            return true;
        }
        return false;
    }

    private String getQueueDataNewPath(String queueName) {
        return getQueueDir(queueName) + "/queue_data_new.txt";
    }

    // 通过这个方法, 真正执行消息数据文件的垃圾回收操作.
    // 使用复制算法来完成.
    // 创建一个新的文件, 名字就是 queue_data_new.txt
    // 把之前消息数据文件中的有效消息都读出来, 写到新的文件中.
    // 删除旧的文件, 再把新的文件改名回 queue_data.txt
    // 同时要记得更新消息统计文件.
    public void gc(MSGQueue queue) throws MqException, IOException, ClassNotFoundException {
        // 进行 gc 的时候, 是针对消息数据文件进行大洗牌. 在这个过程中, 其他线程不能针对该队列的消息文件做任何修改.
        synchronized (queue) {
            // 由于 gc 操作可能比较耗时, 此处统计一下执行消耗的时间.
            long gcBeg = System.currentTimeMillis();

            // 1. 创建一个新的文件
            File queueDataNewFile = new File(getQueueDataNewPath(queue.getName()));
            if (queueDataNewFile.exists()) {
                // 正常情况下, 这个文件不应该存在. 如果存在, 就是意外~~ 说明上次 gc 了一半, 程序意外崩溃了.
                throw new MqException("[MessageFileManager] gc 时发现该队列的 queue_data_new 已经存在! queueName=" + queue.getName());
            }
            boolean ok = queueDataNewFile.createNewFile();
            if (!ok) {
                throw new MqException("[MessageFileManager] 创建文件失败! queueDataNewFile=" + queueDataNewFile.getAbsolutePath());
            }

            // 2. 从旧的文件中, 读取出所有的有效消息对象了. (这个逻辑直接调用上述方法即可, 不必重新写了)
            LinkedList<Message> messages = loadAllMessageFromQueue(queue.getName());

            // 3. 把有效消息, 写入到新的文件中.
            try (OutputStream outputStream = new FileOutputStream(queueDataNewFile)) {
                try (DataOutputStream dataOutputStream = new DataOutputStream(outputStream)) {
                    for (Message message : messages) {
                        byte[] buffer = BinaryTool.toBytes(message);
                        // 先写四个字节消息的长度
                        dataOutputStream.writeInt(buffer.length);
                        dataOutputStream.write(buffer);
                    }
                }
            }

            // 4. 删除旧的数据文件, 并且把新的文件进行重命名
            File queueDataOldFile = new File(getQueueDataPath(queue.getName()));
            ok = queueDataOldFile.delete();
            if (!ok) {
                throw new MqException("[MessageFileManager] 删除旧的数据文件失败! queueDataOldFile=" + queueDataOldFile.getAbsolutePath());
            }
            // 把 queue_data_new.txt => queue_data.txt
            ok = queueDataNewFile.renameTo(queueDataOldFile);
            if (!ok) {
                throw new MqException("[MessageFileManager] 文件重命名失败! queueDataNewFile=" + queueDataNewFile.getAbsolutePath()
                        + ", queueDataOldFile=" + queueDataOldFile.getAbsolutePath());
            }

            // 5. 更新统计文件
            Stat stat = readStat(queue.getName());
            stat.totalCount = messages.size();
            stat.validCount = messages.size();
            writeStat(queue.getName(), stat);

            long gcEnd = System.currentTimeMillis();
            System.out.println("[MessageFileManager] gc 执行完毕! queueName=" + queue.getName() + ", time="
                    + (gcEnd - gcBeg) + "ms");
        }
    }
}
