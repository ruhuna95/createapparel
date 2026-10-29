package com.rexhub.createapparel.citizen;

import com.rexhub.createapparel.entity.Citizen;
import net.minecraft.world.entity.ai.control.MoveControl;

public final class CitizenMoveControl extends MoveControl {
    private final Citizen citizen;

    public CitizenMoveControl(Citizen citizen) {
        super(citizen);
        this.citizen = citizen;
    }

    @Override
    public void tick() {
        if (citizen.standingOnTransport()) {
            operation = Operation.WAIT;
            citizen.setSpeed(0);
            citizen.setZza(0);
            citizen.setXxa(0);
            citizen.setJumping(false);
        } else super.tick();
    }
}
