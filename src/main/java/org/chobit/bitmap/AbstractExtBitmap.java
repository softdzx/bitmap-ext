package org.chobit.bitmap;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 抽象bitmap扩展类。
 * <p>
 * 封装了一系列bitmap扩展类的通用方法
 *
 * @param <T> bitmap扩展类的类型
 * @param <U> bitmap扩展类的子bitmap单元的类型
 * @author rui.zhang
 */
public abstract class AbstractExtBitmap<T extends AbstractExtBitmap<T, U>, U extends IBitmap<U>> implements IBitmap<T> {


    private final List<U> allUnits;


    protected AbstractExtBitmap() {
        this(new ArrayList<>());
    }


    protected AbstractExtBitmap(List<U> units) {
        this.allUnits = units;
    }

    /**
     * 每个bitmap单元的最大容量
     *
     * @return 每个bitmap单元的最大容量
     */
    protected abstract long maxUnitSize();


    /**
     * 根据子单元集合创建新的bitmap实例
     *
     * @param units bitmap子单元集合
     * @return 新建的bitmap
     */
    protected abstract T combine(List<U> units);


    /**
     * 创建新的bitmap单元
     *
     * @return 新的bitmap单元
     */
    protected abstract U newUnit();


    @Override
    public void add(final long offset) {
        checkOffset(offset);
        long index = offset / maxUnitSize();
        long unitOffset = offset % maxUnitSize();
        while (unitsLength() <= index) {
            appendNewUnit();
        }
        getUnit((int) index).add(unitOffset);
    }


    @Override
    public void remove(final long offset) {
        long index = offset / maxUnitSize();
        if (index < unitsLength()) {
            long unitOffset = offset % maxUnitSize();
            getUnit((int) index).remove(unitOffset);
        }
    }


    @Override
    public void add(final long rangeStart, final long rangeEnd) {
        if (rangeStart >= rangeEnd) {
            return;
        }
        checkOffset(rangeEnd);
        long maxIndex = rangeEnd / maxUnitSize();
        while (unitsLength() < maxIndex) {
            appendNewUnit();
        }

        long tmpIndex = rangeStart / maxUnitSize();
        while (tmpIndex <= maxIndex) {
            long start = Math.max(tmpIndex * maxUnitSize(), rangeStart);
            long end = Math.min((tmpIndex + 1) * maxUnitSize(), rangeEnd);
            getUnit((int) tmpIndex).add(start, end);
            tmpIndex++;
        }
    }


    @Override
    public void remove(final long rangeStart, final long rangeEnd) {
        if (rangeStart >= rangeEnd) {
            return;
        }
        checkOffset(rangeEnd);

        long tmpIndex = rangeStart / maxUnitSize();
        while (tmpIndex <= unitsLength()) {
            long start = Math.max(tmpIndex * maxUnitSize(), rangeStart);
            long end = Math.min((tmpIndex + 1) * maxUnitSize(), rangeEnd);
            getUnit((int) tmpIndex).add(start, end);
            tmpIndex++;
        }
    }


    @Override
    public boolean check(long offset) {
        long index = offset / maxUnitSize();
        long unitOffset = offset % maxUnitSize();
        return index < unitsLength() && getUnit((int) index).check(unitOffset);
    }


    @Override
    public T and(T other) {
        return combine(and0(other));
    }


    @Override
    public T or(T another) {
        return combine(or0(another));
    }


    @Override
    public T xor(T another) {
        return combine(xor0(another));
    }


    @Override
    public T andNot(T another) {
        return combine(andNot0(another));
    }


    @Override
    public T not() {
        return combine(not0());
    }


    @Override
    public T clone() {
        return combine(clone0());
    }


    @Override
    public long first() {
        for (int i = 0; i < unitsLength(); i++) {
            long firstInUnit = getUnit(i).first();
            if (firstInUnit != -1) {
                return i * maxUnitSize() + firstInUnit;
            }
        }
        return -1;
    }


    @Override
    public long last() {
        for (int i = unitsLength() - 1; i >= 0; i--) {
            long lastInUnit = getUnit(i).last();
            if (lastInUnit != -1) {
                return i * maxUnitSize() + lastInUnit;
            }
        }
        return -1;
    }


    @Override
    public long size() {
        if (allUnits.isEmpty()) {
            return 0;
        }
        return (unitsLength() - 1) * maxUnitSize() + getUnit(unitsLength() - 1).size();
    }


    @Override
    public long cardinality() {
        long c = 0L;
        for (U u : allUnits) {
            c += u.cardinality();
        }
        return c;
    }


    @Override
    public boolean extend(long newSize) {
        long index = (newSize - 1) / maxUnitSize();
        while (unitsLength() <= index) {
            appendNewUnit();
        }
        boolean extended = false;
        for (int i = 0; i < unitsLength(); i++) {
            if (getUnit(i).extend(newSize - i * maxUnitSize())) {
                extended = true;
            }
        }
        return extended;
    }


    @Override
    public void serialize(DataOutput out) throws IOException {
        for (int i = 0; i < unitsLength(); i++) {
            U u = getUnit(i);
            u.serialize(out);
            if (i < unitsLength() - 1) {
                out.writeByte(i);
            }
        }
    }


    @Override
    public void deserialize(DataInput in) throws IOException {
        boolean hasNext = true;
        while (hasNext) {
            U u = newUnit();
            u.deserialize(in);
            append(u);
            hasNext = in.readByte() != -1;
        }
    }


    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        serialize(out);
    }


    @Override
    public void readExternal(ObjectInput in) throws IOException {
        deserialize(in);
    }


    @Override
    public byte[] toBytes() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        serialize(new DataOutputStream(bos));
        return bos.toByteArray();
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AbstractExtBitmap<T, U> that = (AbstractExtBitmap<T, U>) o;
        return Objects.equals(allUnits, that.allUnits);
    }

    @Override
    public int hashCode() {
        return Objects.hash(allUnits);
    }


    private List<U> and0(AbstractExtBitmap<T, U> o) {
        int count = Math.min(this.unitsLength(), o.unitsLength());
        List<U> andUnits = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            U b1 = this.getUnit(i).clone();
            U b2 = o.getUnit(i).clone();
            append(andUnits, b1.and(b2));
        }
        return andUnits;
    }


    private List<U> or0(AbstractExtBitmap<T, U> o) {
        int count = Math.max(this.unitsLength(), o.unitsLength());
        List<U> orUnits = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            U b1 = i < this.unitsLength() ? this.getUnit(i).clone() : newUnit();
            U b2 = i < o.unitsLength() ? o.getUnit(i).clone() : newUnit();
            append(orUnits, b1.or(b2));
        }
        return orUnits;
    }


    private List<U> xor0(AbstractExtBitmap<T, U> o) {
        int count = Math.max(this.unitsLength(), o.unitsLength());
        List<U> xorUnits = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            U b1 = i < this.unitsLength() ? this.getUnit(i).clone() : newUnit();
            U b2 = i < o.unitsLength() ? o.getUnit(i).clone() : newUnit();
            append(xorUnits, b1.xor(b2));
        }
        return xorUnits;
    }


    private List<U> andNot0(AbstractExtBitmap<T, U> o) {
        List<U> andNotUnits = new ArrayList<>(this.unitsLength());
        for (int i = 0; i < andNotUnits.size(); i++) {
            if (i < o.unitsLength()) {
                append(andNotUnits, this.getUnit(i).andNot(o.getUnit(i)));
            } else {
                append(andNotUnits, this.getUnit(i).clone());
            }
        }
        return andNotUnits;
    }


    private List<U> not0() {
        List<U> notUnits = new ArrayList<>(this.unitsLength());
        for (U unit : allUnits) {
            append(notUnits, unit.not());
        }
        return notUnits;
    }


    protected List<U> clone0() {
        List<U> cloneUnits = new ArrayList<>(this.unitsLength());
        for (U unit : allUnits) {
            append(cloneUnits, unit.clone());
        }
        return cloneUnits;
    }


    private U getUnit(int index) {
        return allUnits.get(index);
    }


    private void checkOffset(long offset) {
        if (offset >= maxUnitSize() * Integer.MAX_VALUE) {
            throw new IllegalArgumentException(String.format("Offset must be less than (%d * Integer.MAX_VALUE). Your offset is %d.", maxUnitSize(), offset));
        }
    }


    private void appendNewUnit() {
        append(newUnit());
    }


    private void append(U bitmap) {
        append(allUnits, bitmap);
    }


    private void append(List<U> bitmaps, U bitmap) {
        int i = unitsLength() - 1;
        while (i >= 0) {
            bitmaps.get(i).extend(maxUnitSize());
            i--;
        }
        bitmaps.add(bitmap);
    }


    protected int unitsLength() {
        return allUnits.size();
    }


    protected void appendWithIndex(int index, U bitmap) {
        if (index > unitsLength()) {
            while (unitsLength() < index) {
                append(newUnit());
            }
            allUnits.add(bitmap);
        } else if (0 == bitmap.size()) {
            allUnits.add(bitmap);
        } else {
            allUnits.set(index, bitmap);
        }
    }

}
