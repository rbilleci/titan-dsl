package titan.dsl.generative;

@FunctionalInterface
interface DslCaseExecutable {
    String run() throws Exception;
}
