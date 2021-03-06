package AntColonyPDP;

import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.core.model.pdp.Vehicle;
import com.github.rinde.rinsim.core.model.pdp.VehicleDTO;
import com.github.rinde.rinsim.geom.Point;
import com.google.common.base.Optional;

public abstract class Ant extends Vehicle {

	protected final double RESTING_RATE;
	protected double energy;
	private static final double SPEED = 1000d;
	protected final double maxEnergy;
	protected boolean resting;
	
	protected Optional<Parcel> curr;
	
	protected Ant(Point startPosition, int capacity) {
		super(VehicleDTO.builder()
				.capacity(capacity)
				.startPosition(startPosition)
				.speed(SPEED)
				.build());
		this.maxEnergy = 5*Environment.MAP_SCALE;
		energy = maxEnergy;
		RESTING_RATE = 0.001*maxEnergy/250;	//takes 250 ticks = 10s to rest
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
			//This is the penalty for the ants 
			//RESTING_RATE / 1 -> low penalty
			//RESTING_RATE / 5 -> medium penalty
			//RESTING_RATE / 10 -> high penalty
			rate = RESTING_RATE / 5; 
		else rate = RESTING_RATE;
		energy += time * rate;	
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
	
	public boolean isTaken() {
		return ! (this.curr.equals(Optional.<Parcel>absent()));
	}
}
