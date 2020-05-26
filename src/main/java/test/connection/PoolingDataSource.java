package test.connection;

import test.connection.readonly.Connection;
import test.connection.readonly.DataSource;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.MethodInterceptor;
import test.connection.readonly.OracleConnection;

public class PoolingDataSource implements DataSource {

    public static final String CALL_CLOSE_METHOD_NAME = "close";
    private final BlockingQueue<Connection> queue;
    //Кэш для классов, для того чтобы не создавать каждый раз прокси.
    private final Map<String, Connection> cacheProxy = new HashMap<>();


    public PoolingDataSource(DataSource dataSource, int poolSize) {
        queue = new ArrayBlockingQueue<>(poolSize);
        for (int i = 0; i < poolSize; i++) {
            addProxyConnection(dataSource.getConnection());
        }
    }

    @Override
    public Connection getConnection() {
        try {
            return queue.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }


    /**
     * Создает прокси и добавляет его в очередь пул коннекшенов
     * При вызове оригинального метода {@link OracleConnection#close()}, коннекш не закрываем а возвращаем обратно в пул.
     *
     * @param connection оригинальный {@link test.connection.readonly.OracleConnection}
     */
    private void addProxyConnection(final Connection connection) {
        final MethodInterceptor handler = (obj, method ,  args,  proxy) -> {
            if(method.getName().equals(CALL_CLOSE_METHOD_NAME)) {
                addProxyConnection(connection);
                return obj;
            } else {
                return proxy.invoke(connection, args);
            }
        };
        //по имено класса кладем в кеш, чтобы разграничивать инстансы объектов
        Connection proxyConnection  = cacheProxy.get(connection.toString());
        if (proxyConnection == null) {
            proxyConnection = (Connection) Enhancer.create(Connection.class, handler);
            cacheProxy.put(connection.toString(), proxyConnection);
        }

        queue.offer(proxyConnection);
    }
}
