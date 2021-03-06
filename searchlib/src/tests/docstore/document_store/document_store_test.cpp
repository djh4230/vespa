// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/searchlib/docstore/documentstore.h>
#include <vespa/searchlib/docstore/cachestats.h>
#include <vespa/document/repo/documenttyperepo.h>

using namespace search;
using CompressionConfig = vespalib::compression::CompressionConfig;

document::DocumentTypeRepo repo;

struct NullDataStore : IDataStore {
    NullDataStore() : IDataStore("") {}
    ssize_t read(uint32_t, vespalib::DataBuffer &) const override { return 0; }
    void read(const LidVector &, IBufferVisitor &) const override { }
    void write(uint64_t, uint32_t, const void *, size_t) override {}
    void remove(uint64_t, uint32_t) override {}
    void flush(uint64_t) override {}
    
    uint64_t initFlush(uint64_t syncToken) override { return syncToken; }

    size_t memoryUsed() const override { return 0; }
    size_t memoryMeta() const override { return 0; }
    size_t getDiskFootprint() const override { return 0; }
    size_t getDiskBloat() const override { return 0; }
    uint64_t lastSyncToken() const override { return 0; }
    uint64_t tentativeLastSyncToken() const override { return 0; }
    fastos::TimeStamp getLastFlushTime() const override { return fastos::TimeStamp(); }
    void accept(IDataStoreVisitor &, IDataStoreVisitorProgress &, bool) override { }
    double getVisitCost() const override { return 1.0; }
    virtual DataStoreStorageStats getStorageStats() const override {
        return DataStoreStorageStats(0, 0, 0.0, 0, 0, 0);
    }
    virtual MemoryUsage getMemoryUsage() const override { return MemoryUsage(); }
    virtual std::vector<DataStoreFileChunkStats>
    getFileChunkStats() const override {
        std::vector<DataStoreFileChunkStats> result;
        return result;
    }
    virtual void compactLidSpace(uint32_t wantedDocLidLimit) override { (void) wantedDocLidLimit; }
    virtual bool canShrinkLidSpace() const override { return false; }
    virtual size_t getEstimatedShrinkLidSpaceGain() const override { return 0; }
    virtual void shrinkLidSpace() override {}
};

TEST_FFF("require that uncache docstore lookups are counted",
         DocumentStore::Config(CompressionConfig::NONE, 0, 0),
         NullDataStore(), DocumentStore(f1, f2))
{
    EXPECT_EQUAL(0u, f3.getCacheStats().misses);
    f3.read(1, repo);
    EXPECT_EQUAL(1u, f3.getCacheStats().misses);
}

TEST_FFF("require that cached docstore lookups are counted",
         DocumentStore::Config(CompressionConfig::NONE, 100000, 100),
         NullDataStore(), DocumentStore(f1, f2))
{
    EXPECT_EQUAL(0u, f3.getCacheStats().misses);
    f3.read(1, repo);
    EXPECT_EQUAL(1u, f3.getCacheStats().misses);
}

TEST_MAIN() { TEST_RUN_ALL(); }
