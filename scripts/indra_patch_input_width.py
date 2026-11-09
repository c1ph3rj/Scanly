"""Give the Indra LiteRT recognizers a usable default input width.

The converted rec*/script models store their image input `x` with a placeholder shape of
[1, H, 1, 1] (W is dynamic). LiteRT allocates tensors once at interpreter creation using that
placeholder, and a width of 1 collapses to 0 inside the network, so creation fails before
`resizeInput` can be called. This rewrites only the stored default shape of graph inputs whose
width is 1 to [1, H, 64, 1]; `shape_signature` (the dynamic dims) is untouched.

Usage: python -I scripts/indra_patch_input_width.py app/src/main/assets/models/indra/*.tflite
"""
import struct
import sys

DEFAULT_WIDTH = 64


def u32(b, o):
    return struct.unpack_from("<I", b, o)[0]


def field(b, table, index):
    """Absolute position of a table field, or None when absent."""
    vtable = table - struct.unpack_from("<i", b, table)[0]
    vt_size = struct.unpack_from("<H", b, vtable)[0]
    slot = 4 + 2 * index
    if slot >= vt_size:
        return None
    off = struct.unpack_from("<H", b, vtable + slot)[0]
    return table + off if off else None


def deref(b, pos):
    return pos + u32(b, pos)


def vector(b, table, index):
    pos = field(b, table, index)
    if pos is None:
        return None, 0
    vec = deref(b, pos)
    return vec + 4, u32(b, vec)


def patch(path):
    b = bytearray(open(path, "rb").read())
    model = u32(b, 0)
    subgraphs, n_subgraphs = vector(b, model, 2)
    patched = 0
    for s in range(n_subgraphs):
        sg = deref(b, subgraphs + 4 * s)
        tensors, _ = vector(b, sg, 0)
        inputs, n_inputs = vector(b, sg, 1)
        for i in range(n_inputs):
            t_index = struct.unpack_from("<i", b, inputs + 4 * i)[0]
            tensor = deref(b, tensors + 4 * t_index)
            shape, rank = vector(b, tensor, 0)
            if rank != 4:
                continue
            dims = list(struct.unpack_from("<4i", b, shape))
            if dims[2] == 1 and dims[1] > 1:
                struct.pack_into("<i", b, shape + 8, DEFAULT_WIDTH)
                print(f"{path}: input {t_index} {dims} -> {[dims[0], dims[1], DEFAULT_WIDTH, dims[3]]}")
                patched += 1
    if patched:
        open(path, "wb").write(bytes(b))
    else:
        print(f"{path}: nothing to patch")


if __name__ == "__main__":
    for p in sys.argv[1:]:
        patch(p)
