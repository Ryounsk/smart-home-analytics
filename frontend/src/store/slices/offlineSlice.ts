import { createSlice, createAsyncThunk, PayloadAction } from '@reduxjs/toolkit';
import { OfflineData, InterfaceStats } from '../../types';
import { 
  getOfflineData, 
  getUsageRanking, 
  getOptimizationSuggestions,
  triggerOfflineAnalysis,
  getTaskStatus
} from '../../services/offline';

interface OfflineState {
  data: OfflineData | null;
  usageRanking: InterfaceStats[];
  optimizationSuggestions: InterfaceStats[];
  loading: boolean;
  error: string | null;
  taskStatus: {
    id: string | null;
    status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | null;
    progress: number;
  };
  timeRange: {
    start: number;
    end: number;
  };
}

const initialState: OfflineState = {
  data: null,
  usageRanking: [],
  optimizationSuggestions: [],
  loading: false,
  error: null,
  taskStatus: {
    id: null,
    status: null,
    progress: 0
  },
  timeRange: {
    start: Date.now() - 30 * 24 * 60 * 60 * 1000, // 30天前
    end: Date.now()
  }
};

// 异步action - 获取离线数据
export const fetchOfflineData = createAsyncThunk(
  'offline/fetchOfflineData',
  async ({ start, end }: { start?: number, end?: number }, { rejectWithValue }) => {
    try {
      return await getOfflineData(start, end);
    } catch (error: any) {
      return rejectWithValue(error.message || '获取离线数据失败');
    }
  }
);

// 异步action - 获取使用率排行榜
export const fetchUsageRanking = createAsyncThunk(
  'offline/fetchUsageRanking',
  async ({ start, end }: { start?: number, end?: number }, { rejectWithValue }) => {
    try {
      return await getUsageRanking(start, end);
    } catch (error: any) {
      return rejectWithValue(error.message || '获取使用率排行榜失败');
    }
  }
);

// 异步action - 获取优化建议
export const fetchOptimizationSuggestions = createAsyncThunk(
  'offline/fetchOptimizationSuggestions',
  async ({ start, end }: { start?: number, end?: number }, { rejectWithValue }) => {
    try {
      return await getOptimizationSuggestions(start, end);
    } catch (error: any) {
      return rejectWithValue(error.message || '获取优化建议失败');
    }
  }
);

// 异步action - 触发离线分析
export const startOfflineAnalysis = createAsyncThunk(
  'offline/startOfflineAnalysis',
  async (_, { rejectWithValue }) => {
    try {
      return await triggerOfflineAnalysis();
    } catch (error: any) {
      return rejectWithValue(error.message || '触发离线分析失败');
    }
  }
);

// 异步action - 获取任务状态
export const checkTaskStatus = createAsyncThunk(
  'offline/checkTaskStatus',
  async (taskId: string, { rejectWithValue }) => {
    try {
      return await getTaskStatus(taskId);
    } catch (error: any) {
      return rejectWithValue(error.message || '获取任务状态失败');
    }
  }
);

const offlineSlice = createSlice({
  name: 'offline',
  initialState,
  reducers: {
    clearError: (state) => {
      state.error = null;
    },
    setTimeRange: (state, action: PayloadAction<{ start: number, end: number }>) => {
      state.timeRange = action.payload;
    },
    resetTaskStatus: (state) => {
      state.taskStatus = {
        id: null,
        status: null,
        progress: 0
      };
    }
  },
  extraReducers: (builder) => {
    builder
      // 处理fetchOfflineData
      .addCase(fetchOfflineData.pending, (state) => {
        state.loading = true;
        state.error = null;
      })
      .addCase(fetchOfflineData.fulfilled, (state, action: PayloadAction<OfflineData>) => {
        state.loading = false;
        state.data = action.payload;
        state.usageRanking = action.payload.usageRanking;
        state.optimizationSuggestions = action.payload.optimizationSuggestions;
      })
      .addCase(fetchOfflineData.rejected, (state, action) => {
        state.loading = false;
        state.error = action.payload as string;
      })
      
      // 处理fetchUsageRanking
      .addCase(fetchUsageRanking.fulfilled, (state, action: PayloadAction<InterfaceStats[]>) => {
        state.usageRanking = action.payload;
        if (state.data) {
          state.data.usageRanking = action.payload;
        }
      })
      
      // 处理fetchOptimizationSuggestions
      .addCase(fetchOptimizationSuggestions.fulfilled, (state, action: PayloadAction<InterfaceStats[]>) => {
        state.optimizationSuggestions = action.payload;
        if (state.data) {
          state.data.optimizationSuggestions = action.payload;
        }
      })
      
      // 处理startOfflineAnalysis
      .addCase(startOfflineAnalysis.pending, (state) => {
        state.loading = true;
        state.error = null;
      })
      .addCase(startOfflineAnalysis.fulfilled, (state, action: PayloadAction<string>) => {
        state.loading = false;
        state.taskStatus = {
          id: action.payload,
          status: 'PENDING',
          progress: 0
        };
      })
      .addCase(startOfflineAnalysis.rejected, (state, action) => {
        state.loading = false;
        state.error = action.payload as string;
      })
      
      // 处理checkTaskStatus
      .addCase(checkTaskStatus.fulfilled, (state, action: PayloadAction<any>) => {
        const { status, progress } = action.payload;
        state.taskStatus.status = status;
        state.taskStatus.progress = progress;
        
        if (status === 'COMPLETED' || status === 'FAILED') {
          state.loading = false;
        }
      });
  }
});

export const { clearError, setTimeRange, resetTaskStatus } = offlineSlice.actions;
export default offlineSlice.reducer;