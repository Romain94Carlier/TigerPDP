package TigerPDP;

import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.core.model.pdp.Vehicle;
import com.github.rinde.rinsim.core.model.pdp.VehicleDTO;
import com.github.rinde.rinsim.core.model.time.TimeLapse;
import com.github.rinde.rinsim.geom.Point;
import com.google.common.base.Optional;

public abstract class Ant extends Vehicle {

	private final double RESTING_RATE;
	private double energy;
	private static final double SPEED = 1000d;
	private final double maxEnergy;	//tune
	private boolean resting;
	
	protected Optional<Parcel> curr;
	
	protected Ant(Point startPosition, int capacity, double maxEnergy) {
		super(VehicleDTO.builder()
				.capacity(capacity)
				.startPosition(startPosition)
				.speed(SPEED)
				.build());
		energy = maxEnergy;
		this.maxEnergy = maxEnergy;
		RESTING_RATE = 0.00000003*maxEnergy/50;
	}

	@Override
	protected void tickImpl(TimeLapse time) {
		// TODO Auto-generated method stub

	}
	
	public double getEnergy() {
		return this.energy;
	}
	
	public double getMaxEnergy() {
		return this.maxEnergy;
	}
	
	protected void decreaseEnergy(double amount) {
		energy -= amount;
	}
	
	protected Optional<Parcel> getCurr() {
		return this.curr;
	}
	
	protected void setCurr(Optional<Parcel> newCurr) {
		this.curr = newCurr;
	}
	
	protected void rest(long time) {
		if(!Environment.mayRest(this))
			return;
		resting = true;
		double rate;
		if(energy < 0)
			rate = RESTING_RATE / 5;
		else rate = RESTING_RATE;
		energy += time * rate;	// tune
		if(energy >= maxEnergy) {
			energy = maxEnergy;
			resting = false;
			Environment.getNearestColony(this).finishedResting(this);
		}
	}
	
	public boolean isResting() {
		return resting;
	}
	
	public Optional<Point> getPosition() {
		return Optional.of(getRoadModel().getPosition(this));
	}
	
	public boolean isTaken() {		//TODO: private? all interactions through communication eventually
		return ! (this.curr.equals(Optional.<Parcel>absent()));
	}
}
