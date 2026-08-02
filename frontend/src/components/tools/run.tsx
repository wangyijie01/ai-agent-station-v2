import { useState } from 'react';

import { useService } from '@flowgram.ai/free-layout-editor';
import { Button } from '@douyinfe/semi-ui';

import { RunningService } from '../../services';

/** 运行当前流程，并同步展示节点与连线的执行状态。 */
export function Run() {
  const [isRunning, setRunning] = useState(false);
  const runningService = useService(RunningService);
  const onRun = async () => {
    setRunning(true);
    await runningService.startRun();
    setRunning(false);
  };
  return (
    <Button
      onClick={onRun}
      loading={isRunning}
      style={{ backgroundColor: 'rgba(171,181,255,0.3)', borderRadius: '8px' }}
    >
      Run
    </Button>
  );
}
