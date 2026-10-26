package ru.misqog.mellhunt.entity;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

/**
 * Ground navigation with three Mellstroy-specific tweaks:
 * - he can path through water (then walks on its surface);
 * - he can path through and open wooden doors;
 * - paths that would drop him more than two blocks are discarded, so he does
 *   not casually walk into deep holes.
 */
public final class MellstroyNavigation extends GroundPathNavigation {

    public MellstroyNavigation(Mob mob, Level level) {
        super(mob, level);
        this.setCanOpenDoors(true);
        this.setCanPassDoors(true);
        this.setCanFloat(true);
    }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        this.nodeEvaluator = new HoleAvoidingNodeEvaluator();
        this.nodeEvaluator.setCanPassDoors(true);
        this.nodeEvaluator.setCanOpenDoors(true);
        this.nodeEvaluator.setCanFloat(true);
        return new PathFinder(this.nodeEvaluator, maxVisitedNodes);
    }

    private static final class HoleAvoidingNodeEvaluator extends WalkNodeEvaluator {

        @Override
        public int getNeighbors(Node[] neighbors, Node current) {
            int count = super.getNeighbors(neighbors, current);
            int kept = 0;
            for (int i = 0; i < count; i++) {
                Node node = neighbors[i];
                // A two-block descent is still acceptable (stairs, small
                // ledges). Anything deeper is treated as a hole.
                if (node.y >= current.y - 2) {
                    neighbors[kept++] = node;
                }
            }
            return kept;
        }
    }
}
