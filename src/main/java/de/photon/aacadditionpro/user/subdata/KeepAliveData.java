package de.photon.aacadditionpro.user.subdata;

import com.google.common.base.Preconditions;
import de.photon.aacadditionpro.user.User;
import de.photon.aacadditionpro.util.datastructures.buffer.ContinuousArrayBuffer;
import de.photon.aacadditionpro.util.datastructures.buffer.ContinuousBuffer;
import lombok.Getter;

import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;

public class KeepAliveData extends SubData
{
    // This needs to be so high to prevent flagging during TimeOuts.
    public static final byte KEEPALIVE_QUEUE_SIZE = 20;

    @Getter
    private final AtomicInteger ignoredKeepAlives = new AtomicInteger(0);

    @Getter
    /* The central Deque of the KeepAlive packet handling.
     *  Synchronized access to the Deque is a must.
     *
     *  Start deleting entries when the queue extends more than 4 times the normal size to prevent crashes.*/
    private final ContinuousBuffer<KeepAlivePacketData> keepAlives = new ContinuousArrayBuffer<KeepAlivePacketData>(KEEPALIVE_QUEUE_SIZE)
    {
        @Override
        public void onForget(KeepAlivePacketData forgotten)
        {
            if (!forgotten.hasRegisteredResponse()) {
                ignoredKeepAlives.getAndIncrement();
            }
        }
    };

    public KeepAliveData(User user)
    {
        super(user);
    }

    /**
     * Calculates how long the client needs to answer a KeepAlive packet on average.
     * Only uses the last 4 values for the calculation.
     */
    public long recentKeepAliveResponseTime() throws IllegalStateException
    {
        synchronized (keepAlives) {
            Preconditions.checkState(!keepAlives.isEmpty(), "KeepAlive queue is empty.");

            long sum = 0;
            byte datapoints = 0;

            final Iterator<KeepAlivePacketData> iterator = keepAlives.descendingIterator();
            KeepAlivePacketData data;

            while (iterator.hasNext() && datapoints <= 3) {
                data = iterator.next();

                // Leave out ignored packets.
                if (data.timeDifference >= 0) {
                    sum += data.timeDifference;
                    ++datapoints;
                }
            }

            Preconditions.checkState(datapoints > 0, "No answered KeepAlive packets found.");
            return sum / datapoints;
        }
    }

    public static class KeepAlivePacketData
    {
        private final long timestamp = System.currentTimeMillis();
        @Getter
        private final long keepAliveID;
        @Getter
        private long timeDifference = -1;

        public KeepAlivePacketData(long keepAliveID) {this.keepAliveID = keepAliveID;}

        public void registerResponse()
        {
            timeDifference = System.currentTimeMillis() - timestamp;
        }

        public boolean hasRegisteredResponse()
        {
            return timeDifference >= 0;
        }

        @Override
        public boolean equals(Object other)
        {
            return this == other ||
                   other != null && getClass() == other.getClass() && keepAliveID == ((KeepAlivePacketData) other).keepAliveID;

        }

        @Override
        public int hashCode()
        {
            return (int) keepAliveID;
        }
    }
}
