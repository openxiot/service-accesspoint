package cc.openxiot.controller.db.ownership;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.Objects;

@RegisterForReflection
public class OwnershipKey {

    public String did;
    public String appId;
    public String ownerId;

    public OwnershipKey() {
    }

    public OwnershipKey(String did, String appId, String ownerId) {
        this.did = did;
        this.appId = appId;
        this.ownerId = ownerId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof OwnershipKey that)) {
            return false;
        }

        return Objects.equals(did, that.did)
                && Objects.equals(appId, that.appId)
                && Objects.equals(ownerId, that.ownerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(did, appId, ownerId);
    }
}
