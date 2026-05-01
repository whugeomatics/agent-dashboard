package local.agent.dashboard;

record SourceFileRecord(long id, long sizeBytes, String modifiedAt, String fileFingerprint) {
    boolean sameFile(SourceFileState state) {
        return sizeBytes == state.sizeBytes()
                && modifiedAt.equals(state.modifiedAt())
                && fileFingerprint.equals(state.fileFingerprint());
    }
}
