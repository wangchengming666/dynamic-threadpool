package cn.hippo4j.config.event;

/**
 * Slow event.
 *
 * @author chen.ma
 * @date 2021/6/23 19:05
 */
public abstract class SlowEvent extends Event {

    @Override
    public long sequence() {
        return 0;
    }

}
