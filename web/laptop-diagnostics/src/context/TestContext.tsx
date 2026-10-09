import { createContext, useCallback, useContext, useEffect, useState, ReactNode } from 'react';

export type TestStatus = 'untested' | 'working' | 'issue' | 'unsupported';
export type TestId = 'keyboard' | 'mouse' | 'camera' | 'microphone' | 'speaker' | 'display' | 'battery' | 'network' | 'sensors';

interface TestContextType {
  results: Record<TestId, TestStatus>;
  setResult: (id: TestId, status: TestStatus) => void;
  resetResult: (id: TestId) => void;
  resetAll: () => void;
}

const defaultResults: Record<TestId, TestStatus> = {
  keyboard: 'untested',
  mouse: 'untested',
  camera: 'untested',
  microphone: 'untested',
  speaker: 'untested',
  display: 'untested',
  battery: 'untested',
  network: 'untested',
  sensors: 'untested',
};

const testIds = Object.keys(defaultResults) as TestId[];
const validStatuses = new Set<TestStatus>(['untested', 'working', 'issue', 'unsupported']);

function isTestStatus(value: unknown): value is TestStatus {
  return typeof value === 'string' && validStatuses.has(value as TestStatus);
}

function loadStoredResults(): Record<TestId, TestStatus> {
  try {
    const stored = localStorage.getItem('saferesale-laptop-results');
    if (!stored) return defaultResults;

    const parsed: unknown = JSON.parse(stored);
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return defaultResults;

    const storedResults = parsed as Record<string, unknown>;
    return testIds.reduce<Record<TestId, TestStatus>>((validated, testId) => {
      if (isTestStatus(storedResults[testId])) validated[testId] = storedResults[testId];
      return validated;
    }, { ...defaultResults });
  } catch (error) {
    console.error('Failed to parse stored results', error);
  }
  return defaultResults;
}

const TestContext = createContext<TestContextType | undefined>(undefined);

export function TestProvider({ children }: { children: ReactNode }) {
  const [results, setResults] = useState<Record<TestId, TestStatus>>(loadStoredResults);

  useEffect(() => {
    try {
      localStorage.setItem('saferesale-laptop-results', JSON.stringify(results));
    } catch (error) {
      console.error('Failed to store test results', error);
    }
  }, [results]);

  const setResult = useCallback((id: TestId, status: TestStatus) => {
    setResults((previous) => ({ ...previous, [id]: status }));
  }, []);

  const resetResult = useCallback((id: TestId) => {
    setResults((previous) => ({ ...previous, [id]: 'untested' }));
  }, []);

  const resetAll = useCallback(() => {
    setResults(defaultResults);
  }, []);

  return (
    <TestContext.Provider value={{ results, setResult, resetResult, resetAll }}>
      {children}
    </TestContext.Provider>
  );
}

export function useTestContext() {
  const context = useContext(TestContext);
  if (context === undefined) {
    throw new Error('useTestContext must be used within a TestProvider');
  }
  return context;
}
