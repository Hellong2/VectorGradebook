package pl.ddconstruction.vectorgradebook.service;

import jdk.incubator.vector.VectorOperators;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorSpecies;
import org.paukov.combinatorics3.Generator;
import org.springframework.stereotype.Service;
import pl.ddconstruction.vectorgradebook.model.CourseConfig;
import pl.ddconstruction.vectorgradebook.model.Student;
import pl.ddconstruction.vectorgradebook.ui.TeamGeneratorView;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class TeamFormationService {

    private final CourseService courseService;
    private static final VectorSpecies<Float> SPECIES = FloatVector.SPECIES_PREFERRED;

    public List<TeamGeneratorView.Team> generateTeams(List<Student> students, boolean preferTrios) {
        if (students.isEmpty())
            return Collections.emptyList();

        CourseConfig config = courseService.getCurrentConfig();
        if (config == null)
            return Collections.emptyList();

        // 1. Prepare Data for Vector API
        // Tags to index
        List<String> sortedTags = new ArrayList<>(config.getAvailableTags());
        int tagCount = sortedTags.size();

        // Student Map for easy lookup by index
        Map<Integer, Student> studentIndexMap = new HashMap<>();
        for (int i = 0; i < students.size(); i++) {
            studentIndexMap.put(i, students.get(i));
        }

        // Grade Matrix: rows = students, cols = tags
        // Pre-calculate all tag averages for all students
        float[][] gradeMatrix = new float[students.size()][tagCount];
        for (int i = 0; i < students.size(); i++) {
            Student s = students.get(i);
            for (int t = 0; t < tagCount; t++) {
                gradeMatrix[i][t] = (float) calculateStudentTagAverage(s, sortedTags.get(t), config);
            }
        }

        // 2. Generate combinations and calculate scores using Matrix
        List<PotentialTeam> allCombinations = generateAllCombinations(students.size(), gradeMatrix, tagCount);

        // 3. Sort by score descending (Greedy approach)
        allCombinations.sort(Comparator.comparingDouble(PotentialTeam::getScore).reversed());

        // 4. Form teams
        List<TeamGeneratorView.Team> finalTeams = new ArrayList<>();
        Set<Integer> assignedIndices = new HashSet<>();

        // If preferTrios, look for trios first
        if (preferTrios) {
            assignTeams(allCombinations, finalTeams, assignedIndices, 3, studentIndexMap);
            assignTeams(allCombinations, finalTeams, assignedIndices, 2, studentIndexMap);
        } else {
            // prefer pairs
            assignTeams(allCombinations, finalTeams, assignedIndices, 2, studentIndexMap);
        }

        // 5. Handle leftovers
        List<Integer> unassignedIndices = new ArrayList<>();
        for (int i = 0; i < students.size(); i++) {
            if (!assignedIndices.contains(i))
                unassignedIndices.add(i);
        }

        handleLeftovers(unassignedIndices, finalTeams, gradeMatrix, tagCount, studentIndexMap);

        return finalTeams;
    }

    private void assignTeams(List<PotentialTeam> combinations,
            List<TeamGeneratorView.Team> finalTeams,
            Set<Integer> assigned,
            int size,
            Map<Integer, Student> studentMap) {
        for (PotentialTeam pt : combinations) {
            if (pt.memberIndices.size() != size)
                continue;

            boolean anyAssigned = pt.memberIndices.stream().anyMatch(assigned::contains);
            if (!anyAssigned) {
                List<Student> members = pt.memberIndices.stream().map(studentMap::get).toList();
                finalTeams.add(new TeamGeneratorView.Team(new ArrayList<>(members), pt.score));
                assigned.addAll(pt.memberIndices);
            }
        }
    }

    private void handleLeftovers(List<Integer> leftovers,
            List<TeamGeneratorView.Team> teams,
            float[][] gradeMatrix,
            int tagCount,
            Map<Integer, Student> studentMap) {

        for (Integer sIdx : leftovers) {
            TeamGeneratorView.Team bestFit = null;
            double bestNewScore = -1;

            // We need to map team back to indices to recalculate...
            // This is a bit expensive but leftovers are few.
            for (TeamGeneratorView.Team team : teams) {
                List<Integer> currentMembers = team.getMembers().stream()
                        .map(s -> findStudentIndex(studentMap, s))
                        .toList();

                List<Integer> newMembers = new ArrayList<>(currentMembers);
                newMembers.add(sIdx);

                double score = calculateTeamScoreVector(newMembers, gradeMatrix, tagCount);
                if (score > bestNewScore) {
                    bestNewScore = score;
                    bestFit = team;
                }
            }

            if (bestFit != null) {
                bestFit.getMembers().add(studentMap.get(sIdx));
                bestFit.setScore(bestNewScore);
            } else {
                teams.add(new TeamGeneratorView.Team(new ArrayList<>(List.of(studentMap.get(sIdx))), 0.0));
            }
        }
    }

    // Helper to find index loop (inefficient but safe for small leftovers)
    private int findStudentIndex(Map<Integer, Student> map, Student s) {
        for (var entry : map.entrySet()) {
            if (entry.getValue().getId().equals(s.getId()))
                return entry.getKey();
        }
        return -1;
    }

    private List<PotentialTeam> generateAllCombinations(int studentCount, float[][] gradeMatrix, int tagCount) {
        List<Integer> indices = IntStream.range(0, studentCount).boxed().toList();

        List<PotentialTeam> pairs = Generator.combination(indices)
                .simple(2)
                .stream()
                .parallel()
                .map(members -> new PotentialTeam(members, calculateTeamScoreVector(members, gradeMatrix, tagCount)))
                .collect(Collectors.toList());

        List<PotentialTeam> trios = Generator.combination(indices)
                .simple(3)
                .stream()
                .parallel()
                .map(members -> new PotentialTeam(members, calculateTeamScoreVector(members, gradeMatrix, tagCount)))
                .toList();

        pairs.addAll(trios);
        return pairs;
    }

    // SIMD Calculation
    private double calculateTeamScoreVector(List<Integer> memberIndices, float[][] gradeMatrix, int tagCount) {
        if (tagCount == 0)
            return 0.0;

        // Result vector accumulating the Max per tag
        // We iterate over tags in steps of SPECIES.length()

        float sumMaxGrades = 0.0f;
        int i = 0;
        int upperBound = SPECIES.loopBound(tagCount);

        // Vector Loop
        for (; i < upperBound; i += SPECIES.length()) {
            // Init with MIN_VALUE or just the first student's values
            FloatVector maxVec = FloatVector.fromArray(SPECIES, gradeMatrix[memberIndices.getFirst()], i);

            // Max against other students
            for (int m = 1; m < memberIndices.size(); m++) {
                FloatVector vec = FloatVector.fromArray(SPECIES, gradeMatrix[memberIndices.get(m)], i);
                maxVec = maxVec.max(vec);
            }

            // Add reduced sum of this chunk
            sumMaxGrades += maxVec.reduceLanes(VectorOperators.ADD);
        }

        // Post-loop for remaining elements
        for (; i < tagCount; i++) {
            float maxVal = gradeMatrix[memberIndices.getFirst()][i];
            for (int m = 1; m < memberIndices.size(); m++) {
                maxVal = Math.max(maxVal, gradeMatrix[memberIndices.get(m)][i]);
            }
            sumMaxGrades += maxVal;
        }

        return sumMaxGrades / tagCount;
    }

    private double calculateStudentTagAverage(Student student, String tag, CourseConfig config) {
        List<Double> grades = new ArrayList<>();
        config.getClasses().forEach(cls -> {
            if (cls.getTags().contains(tag)) {
                Double g = student.getClassGrades().get(cls.getId());
                if (g != null)
                    grades.add(g);
            }
        });

        return grades.stream().mapToDouble(d -> d).average().orElse(0.0);
    }

    private static class PotentialTeam {
        List<Integer> memberIndices;
        @Getter
        double score;

        public PotentialTeam(List<Integer> memberIndices, double score) {
            this.memberIndices = memberIndices;
            this.score = score;
        }

    }
}
