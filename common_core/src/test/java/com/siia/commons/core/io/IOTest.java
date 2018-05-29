package com.siia.commons.core.io;

import org.junit.Test;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;


public class IOTest {

    @Test
    public void destBufferIsSmallerThanSrc() {

        ByteBuffer src = ByteBuffer.wrap(new byte[]{1,2,3});
        ByteBuffer dest = ByteBuffer.allocate(2);

        int count = IO.copyUntilDestinationFull(src, dest);
        assertThat(count).isEqualTo(2);
        assertThat(dest.limit()).isEqualTo(2);
        assertThat(dest.capacity()).isEqualTo(2);
        assertThat(dest.position()).isEqualTo(2);
        assertThat(dest.array()).isEqualTo(new byte[]{1,2});
    }

    @Test
    public void destBufferIsLargerThanSrc() {

        ByteBuffer src = ByteBuffer.wrap(new byte[]{1,2,3});
        ByteBuffer dest = ByteBuffer.allocate(5);

        int count = IO.copyUntilDestinationFull(src, dest);
        assertThat(count).isEqualTo(3);
        assertThat(dest.limit()).isEqualTo(5);
        assertThat(dest.capacity()).isEqualTo(5);
        assertThat(dest.position()).isEqualTo(3);
        assertThat(dest.array()).isEqualTo(new byte[]{1,2,3,0,0});
    }

    @Test
    public void destBufferIsEqualToSrc() {

        ByteBuffer src = ByteBuffer.wrap(new byte[]{1,2,3,4});
        ByteBuffer dest = ByteBuffer.allocate(4);

        int count = IO.copyUntilDestinationFull(src, dest);
        assertThat(count).isEqualTo(4);
        assertThat(dest.limit()).isEqualTo(4);
        assertThat(dest.capacity()).isEqualTo(4);
        assertThat(dest.position()).isEqualTo(4);
        assertThat(dest.array()).isEqualTo(new byte[]{1,2,3,4});
    }

    @Test
    public void destBufferIsSmallerThanSrcAndPartialRead() {

        ByteBuffer src = ByteBuffer.wrap(new byte[]{1,2,3,4});
        ByteBuffer dest = ByteBuffer.allocate(8);
        dest.position(4);

        int count = IO.copyUntilDestinationFull(src, dest);
        assertThat(count).isEqualTo(4);
        assertThat(dest.limit()).isEqualTo(8);
        assertThat(dest.capacity()).isEqualTo(8);
        assertThat(dest.position()).isEqualTo(8);
        assertThat(dest.array()).isEqualTo(new byte[]{0,0,0,0,1,2,3,4});
    }

    @Test
    public void destBufferIsLargerAndBothInPartialRead() {

        ByteBuffer src = ByteBuffer.wrap(new byte[]{1,2,3,4});
        src.position(2);
        ByteBuffer dest = ByteBuffer.allocate(8);
        dest.position(4);

        int count = IO.copyUntilDestinationFull(src, dest);
        assertThat(count).isEqualTo(2);
        assertThat(dest.limit()).isEqualTo(8);
        assertThat(dest.capacity()).isEqualTo(8);
        assertThat(dest.position()).isEqualTo(6);
        assertThat(dest.array()).isEqualTo(new byte[]{0,0,0,0,3,4,0,0});
    }


}