#include "msgq/visionipc/visionbuf.h"

#include <android/sharedmem.h>

#include <cerrno>
#include <cstdint>
#include <cstring>

#include <sys/mman.h>
#include <unistd.h>


void VisionBuf::allocate(size_t length) {

  len = length;

  /*
   * Keep upstream VisionIPC's frame-id word immediately
   * after the image payload.
   */
  mmap_len =
      len + sizeof(uint64_t);

  fd = ASharedMemory_create(
      "p4pilot_visionipc",
      mmap_len
  );

  if (fd < 0) {
    addr = nullptr;
    return;
  }

  if (ASharedMemory_setProt(
          fd,
          PROT_READ | PROT_WRITE
      ) != 0) {

    close(fd);
    fd = -1;
    addr = nullptr;
    return;
  }

  addr = mmap(
      nullptr,
      mmap_len,
      PROT_READ | PROT_WRITE,
      MAP_SHARED,
      fd,
      0
  );

  if (addr == MAP_FAILED) {
    addr = nullptr;
    close(fd);
    fd = -1;
    return;
  }

  std::memset(
      addr,
      0,
      mmap_len
  );

  frame_id =
      reinterpret_cast<uint64_t *>(
          reinterpret_cast<uint8_t *>(addr) +
          len
      );
}


void VisionBuf::import() {

  if (fd < 0) {
    addr = nullptr;
    return;
  }

  addr = mmap(
      nullptr,
      mmap_len,
      PROT_READ | PROT_WRITE,
      MAP_SHARED,
      fd,
      0
  );

  if (addr == MAP_FAILED) {
    addr = nullptr;
    return;
  }

  frame_id =
      reinterpret_cast<uint64_t *>(
          reinterpret_cast<uint8_t *>(addr) +
          len
      );
}


void VisionBuf::init_yuv(
    size_t init_width,
    size_t init_height,
    size_t init_stride,
    size_t init_uv_offset) {

  width = init_width;
  height = init_height;
  stride = init_stride;
  uv_offset = init_uv_offset;

  y =
      reinterpret_cast<uint8_t *>(addr);

  uv =
      y + uv_offset;
}


int VisionBuf::sync(int) {
  /*
   * CPU-written ASharedMemory is already coherent for the
   * producer/consumer model used in Step61.
   */
  return 0;
}


int VisionBuf::free() {

  int result = 0;

  if (addr != nullptr &&
      mmap_len > 0) {

    if (munmap(
            addr,
            mmap_len
        ) != 0) {

      result = errno;
    }
  }

  addr = nullptr;
  frame_id = nullptr;
  y = nullptr;
  uv = nullptr;

  if (fd >= 0) {

    if (close(fd) != 0 &&
        result == 0) {

      result = errno;
    }
  }

  fd = -1;

  return result;
}


uint64_t VisionBuf::get_frame_id() {

  if (frame_id == nullptr) {
    return 0;
  }

  return *frame_id;
}


void VisionBuf::set_frame_id(
    uint64_t id) {

  if (frame_id != nullptr) {
    *frame_id = id;
  }
}
