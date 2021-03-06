// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "predicate_ref_cache.h"
#include <vespa/searchlib/datastore/bufferstate.h>
#include <vespa/searchlib/datastore/datastore.h>
#include <vespa/searchlib/datastore/entryref.h>
#include <vector>

namespace search {
namespace predicate {
class Interval;

/**
 * Stores interval entries in a memory-efficient way.
 * It works with both Interval and IntervalWithBounds entries.
 */
class PredicateIntervalStore {
    class DataStoreAdapter;
    typedef PredicateRefCache<DataStoreAdapter, 8> RefCacheType;
    typedef datastore::DataStoreT<datastore::EntryRefT<18, 6>> DataStoreType;
    typedef DataStoreType::RefType RefType;
    using generation_t = vespalib::GenerationHandler::generation_t;

    DataStoreType _store;
    datastore::BufferType<uint32_t> _size1Type;

    class DataStoreAdapter {
        const DataStoreType &_store;
    public:
        DataStoreAdapter(const DataStoreType &store) : _store(store) {}
        const uint32_t *getBuffer(uint32_t ref) const {
            RefType entry_ref(ref);
            return _store.getBufferEntry<uint32_t>(
                    entry_ref.bufferId(), entry_ref.offset());
        }
    };
    DataStoreAdapter _store_adapter;
    RefCacheType _ref_cache;

    // Return type for private allocation functions
    template <typename T>
    struct Entry {
        RefType ref;
        T *buffer;
    };

    // Allocates a new entry in a datastore buffer.
    template <typename T>
    Entry<T> allocNewEntry(uint32_t type_id, uint32_t size);
    // Returns the size of an interval entry in number of uint32_t.
    template <typename IntervalT>
    static uint32_t entrySize() { return sizeof(IntervalT) / sizeof(uint32_t); }

public:
    PredicateIntervalStore();
    ~PredicateIntervalStore();

    /**
     * Inserts an array of intervals into the store.
     * IntervalT is either Interval or IntervalWithBounds.
     */
    template <typename IntervalT>
    datastore::EntryRef insert(const std::vector<IntervalT> &intervals);

    /**
     * Removes an entry. The entry remains accessible until commit
     * is called, and also as long as readers hold the current
     * generation.
     *
     * Remove is currently disabled, as the ref cache is assumed to
     * keep the total number of different entries low.
     */
    void remove(datastore::EntryRef ref);

    void trimHoldLists(generation_t used_generation);

    void transferHoldLists(generation_t generation);

    /**
     * Return memory usage (only the data store is included)
     */
    MemoryUsage getMemoryUsage() const {
        return _store.getMemoryUsage();
    }

    /**
     * Retrieves a list of intervals.
     * IntervalT is either Interval or IntervalWithBounds.
     * single_buf is a pointer to a single IntervalT, used by the
     * single interval optimization.
     */
    template <typename IntervalT>
    const IntervalT *get(datastore::EntryRef btree_ref,
                         uint32_t &size_out,
                         IntervalT *single_buf) const
    {
        uint32_t size = btree_ref.ref() >> RefCacheType::SIZE_SHIFT;
        RefType data_ref(btree_ref.ref() & RefCacheType::DATA_REF_MASK);
        if (__builtin_expect(size == 0, true)) {  // single-interval optimization
            *single_buf = IntervalT();
            single_buf->interval = data_ref.ref();
            size_out = 1;
            return single_buf;
        }
        const uint32_t *buf =
            _store.getBufferEntry<uint32_t>(data_ref.bufferId(),
                                            data_ref.offset());
        if (size == RefCacheType::MAX_SIZE) {
            size = *buf++;
        }
        size_out = size / entrySize<IntervalT>();
        return reinterpret_cast<const IntervalT *>(buf);
    }
};
}  // namespace predicate
}  // namespace search

