package cc.openxiot.controller.db.ownership;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.Objects;

@RegisterForReflection
public class OwnershipId {

    public String did;
    public String appId;
    public String ownerId;

    public OwnershipId() {
    }

    public OwnershipId(String did, String appId, String ownerId) {
        this.did = did;
        this.appId = appId;
        this.ownerId = ownerId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof OwnershipId that)) {
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
